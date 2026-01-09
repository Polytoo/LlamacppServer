package org.mark.llamacpp.server.tools;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mark.llamacpp.server.struct.VramEstimation;

public final class VramEstimator {

	private static final int MODEL_INFO_CACHE_MAX_ENTRIES = 32;

	private static final Map<String, CachedModelInfo> MODEL_INFO_CACHE = Collections
			.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
				private static final long serialVersionUID = 1L;

				@Override
				protected boolean removeEldestEntry(Map.Entry<String, CachedModelInfo> eldest) {
					return size() > MODEL_INFO_CACHE_MAX_ENTRIES;
				}
			});

	private record FileStamp(String path, long lastModified, long length) {
		static FileStamp from(File f) {
			return new FileStamp(f.getAbsolutePath(), f.lastModified(), f.length());
		}
	}

	private record CachedModelInfo(List<FileStamp> parts, ModelParams params, long weightsBytes, long kvLayerCount) {
	}

	public enum KvCacheType {
		F32("f32", 4.0),
		F16("f16", 2.0),
		BF16("bf16", 2.0),
		Q8_0("q8_0", 36.0 / 32.0),
		Q4_0("q4_0", 18.0 / 32.0),
		Q4_1("q4_1", 20.0 / 32.0),
		IQ4_NL("iq4_nl", 18.0 / 32.0),
		Q5_0("q5_0", 22.0 / 32.0),
		Q5_1("q5_1", 24.0 / 32.0);

		private final String id;
		private final double bytesPerElement;

		KvCacheType(String id, double bytesPerElement) {
			this.id = id;
			this.bytesPerElement = bytesPerElement;
		}

		public String id() {
			return id;
		}

		public double bytesPerElement() {
			return bytesPerElement;
		}

		public static KvCacheType from(String input) {
			if (input == null || input.isBlank()) {
				throw new IllegalArgumentException("kv缓存数据类型不能为空");
			}
			String normalized = input.trim().toLowerCase(Locale.ROOT);
			for (KvCacheType t : values()) {
				if (t.id.equals(normalized)) {
					return t;
				}
			}
			throw new IllegalArgumentException("不支持的kv缓存数据类型: " + input + "，可选: " + Arrays.toString(values()));
		}
	}

	public static final class Estimate {
		private final long modelWeightsBytes;
		private final long kvCacheBytes;
		private final long runtimeOverheadBytes;
		private final long totalBytes;
		private final String architecture;
		private final long contextLength;
		private final KvCacheType kvCacheTypeK;
		private final KvCacheType kvCacheTypeV;
		private final boolean flashAttention;

		public Estimate(long modelWeightsBytes, long kvCacheBytes, long runtimeOverheadBytes, String architecture,
				long contextLength, KvCacheType kvCacheType, boolean flashAttention) {
			this(modelWeightsBytes, kvCacheBytes, runtimeOverheadBytes, architecture, contextLength, kvCacheType, kvCacheType,
					flashAttention);
		}

		public Estimate(long modelWeightsBytes, long kvCacheBytes, long runtimeOverheadBytes, String architecture,
				long contextLength, KvCacheType kvCacheTypeK, KvCacheType kvCacheTypeV, boolean flashAttention) {
			this.modelWeightsBytes = modelWeightsBytes;
			this.kvCacheBytes = kvCacheBytes;
			this.runtimeOverheadBytes = runtimeOverheadBytes;
			this.totalBytes = safeAdd(safeAdd(modelWeightsBytes, kvCacheBytes), runtimeOverheadBytes);
			this.architecture = architecture;
			this.contextLength = contextLength;
			this.kvCacheTypeK = kvCacheTypeK;
			this.kvCacheTypeV = kvCacheTypeV;
			this.flashAttention = flashAttention;
		}

		public long getModelWeightsBytes() {
			return modelWeightsBytes;
		}

		public long getKvCacheBytes() {
			return kvCacheBytes;
		}

		public long getRuntimeOverheadBytes() {
			return runtimeOverheadBytes;
		}

		public long getTotalBytes() {
			return totalBytes;
		}

		public String getArchitecture() {
			return architecture;
		}

		public long getContextLength() {
			return contextLength;
		}

		public KvCacheType getKvCacheType() {
			return kvCacheTypeK;
		}

		public KvCacheType getKvCacheTypeK() {
			return kvCacheTypeK;
		}

		public KvCacheType getKvCacheTypeV() {
			return kvCacheTypeV;
		}

		public boolean isFlashAttention() {
			return flashAttention;
		}

		public double getTotalGiB() {
			return totalBytes / (1024.0 * 1024.0 * 1024.0);
		}

		public double getModelWeightsGiB() {
			return modelWeightsBytes / (1024.0 * 1024.0 * 1024.0);
		}

		public double getKvCacheGiB() {
			return kvCacheBytes / (1024.0 * 1024.0 * 1024.0);
		}

		public double getRuntimeOverheadGiB() {
			return runtimeOverheadBytes / (1024.0 * 1024.0 * 1024.0);
		}
	}

	private static final class ModelParams {
		private final String architecture;
		private final long nLayer;
		private final long nEmbd;
		private final long nHeadKv;
		private final long headDim;

		private ModelParams(String architecture, long nLayer, long nEmbd, long nHeadKv, long headDim) {
			this.architecture = architecture;
			this.nLayer = nLayer;
			this.nEmbd = nEmbd;
			this.nHeadKv = nHeadKv;
			this.headDim = headDim;
		}
	}

	private static final Pattern SPLIT_OF_PATTERN = Pattern.compile("^(.*)-(\\d{5})-of-(\\d{5})\\.gguf$",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern SPLIT_DOT_PATTERN = Pattern.compile("^(.*)\\.gguf\\.(\\d+)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern LAYER_INDEX_PREFIX_PATTERN = Pattern.compile("^(?:blk|block|layer|layers)\\.(\\d+)\\.",
			Pattern.CASE_INSENSITIVE);
	
	
	private VramEstimator() {
		
	}

	public static void clearCache() {
		MODEL_INFO_CACHE.clear();
	}

	public static VramEstimation estimateVram(File modelPath, int ctxSize, int kvTypeBits, int batchSize, int ubatchSize)
			throws IOException {
		return estimateVram(modelPath, ctxSize, kvTypeBits, batchSize, ubatchSize, true);
	}

	public static VramEstimation estimateVram(File modelPath, int ctxSize, int kvTypeBits, int batchSize, int ubatchSize,
			boolean enableVision) throws IOException {
		KvCacheType kvType = kvCacheTypeFromLegacyBits(kvTypeBits);
		Estimate est = estimate(modelPath, ctxSize, kvType, true);
		return new VramEstimation(est.getTotalBytes(), est.getModelWeightsBytes(), est.getKvCacheBytes(),
				est.getRuntimeOverheadBytes());
	}

	private static KvCacheType kvCacheTypeFromLegacyBits(int kvTypeBits) {
		return switch (kvTypeBits) {
		case 16 -> KvCacheType.F16;
		case 32 -> KvCacheType.F32;
		default -> throw new IllegalArgumentException("不支持的kv缓存类型位宽: " + kvTypeBits + "，可选: 16, 32");
		};
	}

	public static Estimate estimate(String modelPath, int contextLength, KvCacheType kvCacheType,
			boolean flashAttention) throws IOException {
		Objects.requireNonNull(modelPath, "modelPath");
		return estimate(new File(modelPath), contextLength, kvCacheType, flashAttention);
	}

	public static Estimate estimate(File modelPath, int contextLength, KvCacheType kvCacheType, boolean flashAttention)
			throws IOException {
		return estimate(modelPath, contextLength, kvCacheType, kvCacheType, flashAttention);
	}

	public static Estimate estimate(File modelPath, int contextLength, KvCacheType kvCacheTypeK, KvCacheType kvCacheTypeV,
			boolean flashAttention) throws IOException {
		Objects.requireNonNull(modelPath, "modelPath");
		if (contextLength <= 0) {
			throw new IllegalArgumentException("contextLength 必须大于0");
		}

		ResolvedBundle bundle = resolveBundle(modelPath);
		if (bundle.primaryFile == null || bundle.parts.isEmpty()) {
			throw new IllegalArgumentException("未找到可用的GGUF文件: " + modelPath.getAbsolutePath());
		}

		CachedModelInfo info = getOrComputeCachedModelInfo(bundle);
		ModelParams params = info.params;
		long kvCacheBytes = estimateKvCacheBytes(params, contextLength, kvCacheTypeK, kvCacheTypeV, info.kvLayerCount);
		long runtimeOverhead = estimateRuntimeOverheadBytes(params, contextLength, kvCacheBytes, flashAttention);

		return new Estimate(info.weightsBytes, kvCacheBytes, runtimeOverhead, params.architecture, contextLength,
				kvCacheTypeK, kvCacheTypeV, flashAttention);
	}

	private static CachedModelInfo getOrComputeCachedModelInfo(ResolvedBundle bundle) throws IOException {
		String key = bundle.primaryFile.getAbsolutePath();
		List<FileStamp> current = new ArrayList<>(bundle.parts.size());
		for (File f : bundle.parts) {
			current.add(FileStamp.from(f));
		}

		CachedModelInfo cached = MODEL_INFO_CACHE.get(key);
		if (cached != null && stampsMatch(cached.parts, current)) {
			return cached;
		}

		Map<String, Object> primaryMeta = readGgufMetadata(bundle.primaryFile);
		ModelParams params = extractModelParams(primaryMeta);

		long weightsBytes = 0;
		for (File part : bundle.parts) {
			weightsBytes = safeAdd(weightsBytes, estimateTensorDataBytes(part));
		}

		long kvLayerCount = estimateKvLayerCount(bundle.parts, params.nLayer);
		CachedModelInfo computed = new CachedModelInfo(List.copyOf(current), params, weightsBytes, kvLayerCount);
		MODEL_INFO_CACHE.put(key, computed);
		return computed;
	}

	private static boolean stampsMatch(List<FileStamp> a, List<FileStamp> b) {
		if (a == null || b == null || a.size() != b.size()) {
			return false;
		}
		for (int i = 0; i < a.size(); i++) {
			FileStamp x = a.get(i);
			FileStamp y = b.get(i);
			if (!Objects.equals(x.path, y.path) || x.lastModified != y.lastModified || x.length != y.length) {
				return false;
			}
		}
		return true;
	}

	private static long estimateKvCacheBytes(ModelParams params, long contextLength, KvCacheType kvTypeK, KvCacheType kvTypeV,
			long kvLayerCount) {
		long layers = kvLayerCount > 0 ? kvLayerCount : params.nLayer;
		if (layers <= 0 || params.nHeadKv <= 0 || params.headDim <= 0) {
			return 0;
		}
		double bytesPerElement = kvTypeK.bytesPerElement() + kvTypeV.bytesPerElement();
		double bytes = layers * contextLength * params.nHeadKv * params.headDim * bytesPerElement;
		if (bytes <= 0) {
			return 0;
		}
		if (bytes >= Long.MAX_VALUE) {
			return Long.MAX_VALUE;
		}
		return (long) bytes;
	}

	private static long estimateRuntimeOverheadBytes(ModelParams params, long contextLength, long kvCacheBytes,
			boolean flashAttention) {
		long base = 256L * 1024 * 1024;
		long flashExtra = 0;
		if (flashAttention) {
			long byKv = kvCacheBytes / 20;
			long byCtx = 0;
			if (params.nEmbd > 0) {
				double b = contextLength * (double) params.nEmbd * 2.0;
				if (b > 0 && b < Long.MAX_VALUE) {
					byCtx = (long) b;
				}
			}
			flashExtra = safeAdd(64L * 1024 * 1024, Math.max(byKv, byCtx));
		}
		return safeAdd(base, flashExtra);
	}

	private static long estimateKvLayerCount(List<File> ggufParts, long expectedLayerCount) throws IOException {
		if (expectedLayerCount <= 0) {
			return 0;
		}
		boolean[] hasAttention = new boolean[(int) Math.min(expectedLayerCount, Integer.MAX_VALUE)];
		for (File part : ggufParts) {
			markAttentionLayers(part, hasAttention);
		}
		long count = 0;
		for (boolean v : hasAttention) {
			if (v) {
				count++;
			}
		}
		return count;
	}

	private static void markAttentionLayers(File ggufFile, boolean[] hasAttention) throws IOException {
		try (RandomAccessFile raf = new RandomAccessFile(ggufFile, "r"); FileChannel ch = raf.getChannel()) {
			long fileSize = ch.size();
			LeReader r = new LeReader(ch, Math.min(32L * 1024 * 1024, fileSize));

			byte[] magic = r.readBytes(4);
			String m = new String(magic, StandardCharsets.US_ASCII);
			if (!"GGUF".equals(m)) {
				return;
			}

			r.readI32();
			long tensorCount = r.readU64();
			long kvCount = r.readU64();

			for (long i = 0; i < kvCount; i++) {
				String key = r.readGgufString();
				int type = r.readI32();
				if ("tokenizer.ggml.tokens".equals(key) && type == 9) {
					int elemType = r.readI32();
					long len = r.readU64();
					for (long j = 0; j < len; j++) {
						r.skipGgufValue(elemType);
					}
				} else {
					r.skipGgufValue(type);
				}
			}

			for (long i = 0; i < tensorCount; i++) {
				String name = r.readGgufString();
				int nDims = r.readI32();
				for (int d = 0; d < nDims; d++) {
					r.readU64();
				}
				r.readI32();
				r.readU64();

				int idx = extractLayerIndex(name);
				if (idx < 0 || idx >= hasAttention.length) {
					continue;
				}
				if (isAttentionQkvTensor(name)) {
					hasAttention[idx] = true;
				}
			}
		} catch (EOFException eof) {
		}
	}

	private static int extractLayerIndex(String tensorName) {
		if (tensorName == null || tensorName.isBlank()) {
			return -1;
		}
		Matcher m = LAYER_INDEX_PREFIX_PATTERN.matcher(tensorName);
		if (!m.find()) {
			return -1;
		}
		try {
			return Integer.parseInt(m.group(1));
		} catch (Exception e) {
			return -1;
		}
	}

	private static boolean isAttentionQkvTensor(String tensorName) {
		if (tensorName == null || tensorName.isBlank()) {
			return false;
		}
		String n = tensorName.toLowerCase(Locale.ROOT);
		boolean isAttn = n.contains("attn") || n.contains("self_attn") || n.contains("attention");
		if (!isAttn) {
			return false;
		}
		return n.contains("attn_q") || n.contains("attn_k") || n.contains("attn_v") || n.contains("q_proj")
				|| n.contains("k_proj") || n.contains("v_proj") || n.contains(".wq") || n.contains(".wk") || n.contains(".wv")
				|| n.contains(".query") || n.contains(".key") || n.contains(".value");
	}

	private static ResolvedBundle resolveBundle(File input) {
		if (!input.exists()) {
			throw new IllegalArgumentException("文件不存在: " + input.getAbsolutePath());
		}

		File seed = input;
		if (input.isDirectory()) {
			File[] ggufs = input.listFiles((dir, name) -> name != null && name.toLowerCase(Locale.ROOT).endsWith(".gguf"));
			if (ggufs == null || ggufs.length == 0) {
				return new ResolvedBundle(null, List.of());
			}
			seed = pickSeedFile(ggufs);
		}

		Matcher ofMatcher = SPLIT_OF_PATTERN.matcher(seed.getName());
		if (ofMatcher.matches()) {
			String base = ofMatcher.group(1);
			int total = Integer.parseInt(ofMatcher.group(3));
			File dir = seed.getParentFile() == null ? new File(".") : seed.getParentFile();
			List<File> parts = new ArrayList<>(total);
			for (int i = 1; i <= total; i++) {
				File f = new File(dir, String.format("%s-%05d-of-%05d.gguf", base, i, total));
				if (f.exists()) {
					parts.add(f);
				}
			}
			File primary = new File(dir, String.format("%s-00001-of-%05d.gguf", base, total));
			if (!primary.exists() && !parts.isEmpty()) {
				primary = parts.getFirst();
			}
			return new ResolvedBundle(primary, parts);
		}

		Matcher dotMatcher = SPLIT_DOT_PATTERN.matcher(seed.getName());
		if (dotMatcher.matches()) {
			String base = dotMatcher.group(1);
			File dir = seed.getParentFile() == null ? new File(".") : seed.getParentFile();
			List<File> parts = new ArrayList<>();
			int idx = 1;
			while (true) {
				File f = new File(dir, base + ".gguf." + idx);
				if (!f.exists()) {
					break;
				}
				parts.add(f);
				idx++;
			}
			File primary = new File(dir, base + ".gguf.1");
			if (!primary.exists() && !parts.isEmpty()) {
				primary = parts.getFirst();
			}
			return new ResolvedBundle(primary, parts);
		}

		return new ResolvedBundle(seed, List.of(seed));
	}

	private static File pickSeedFile(File[] ggufs) {
		for (File f : ggufs) {
			String n = f.getName().toLowerCase(Locale.ROOT);
			if (n.matches(".*-00001-of-\\d{5}\\.gguf$")) {
				return f;
			}
		}
		for (File f : ggufs) {
			String n = f.getName().toLowerCase(Locale.ROOT);
			if (!n.contains("mmproj")) {
				return f;
			}
		}
		return ggufs[0];
	}

	private static final class ResolvedBundle {
		private final File primaryFile;
		private final List<File> parts;

		private ResolvedBundle(File primaryFile, List<File> parts) {
			this.primaryFile = primaryFile;
			this.parts = parts;
		}
	}

	private static Map<String, Object> readGgufMetadata(File ggufFile) throws IOException {
		try (RandomAccessFile raf = new RandomAccessFile(ggufFile, "r"); FileChannel ch = raf.getChannel()) {
			LeReader r = new LeReader(ch, Math.min(8L * 1024 * 1024, ch.size()));
			byte[] magic = r.readBytes(4);
			String m = new String(magic, StandardCharsets.US_ASCII);
			if (!"GGUF".equals(m)) {
				throw new IllegalArgumentException("不是有效GGUF文件: " + ggufFile.getAbsolutePath());
			}
			r.readI32();
			long tensorCount = r.readU64();
			long kvCount = r.readU64();

			Map<String, Object> out = new HashMap<>();
			out.put("__tensor_count", tensorCount);
			out.put("__kv_count", kvCount);

			for (long i = 0; i < kvCount; i++) {
				String key = r.readGgufString();
				int type = r.readI32();
				if ("tokenizer.ggml.tokens".equals(key) && type == 9) {
					int elemType = r.readI32();
					long len = r.readU64();
					for (long j = 0; j < len; j++) {
						r.skipGgufValue(elemType);
					}
					out.put(key + ".size", len);
				} else {
					Object val = r.readGgufValue(type);
					out.put(key, val);
				}
			}

			return out;
		}
	}

	private static ModelParams extractModelParams(Map<String, Object> meta) {
		String arch = asString(meta.get("general.architecture"));
		if (arch == null || arch.isBlank()) {
			arch = findBySuffix(meta, ".architecture");
		}
		if (arch == null || arch.isBlank()) {
			arch = "unknown";
		}

		long nEmbd = firstLong(meta, arch + ".embedding_length", findKeyBySuffix(meta, ".embedding_length"));
		long nLayer = firstLong(meta, arch + ".block_count", findKeyBySuffix(meta, ".block_count"));
		long nHead = firstLong(meta, arch + ".attention.head_count", findKeyBySuffix(meta, ".attention.head_count"));
		long nHeadKv = firstLong(meta, arch + ".attention.head_count_kv", findKeyBySuffix(meta, ".attention.head_count_kv"));
		if (nHeadKv == 0) {
			nHeadKv = nHead;
		}

		long keyLength = firstLong(meta, arch + ".attention.key_length", findKeyBySuffix(meta, ".attention.key_length"));
		long headDim = 0;
		if (keyLength > 0) {
			headDim = keyLength;
		} else if (nEmbd > 0 && nHead > 0) {
			headDim = nEmbd / nHead;
		}

		return new ModelParams(arch, nLayer, nEmbd, nHeadKv, headDim);
	}

	private static long estimateTensorDataBytes(File ggufFile) throws IOException {
		try (RandomAccessFile raf = new RandomAccessFile(ggufFile, "r"); FileChannel ch = raf.getChannel()) {
			long fileSize = ch.size();
			LeReader r = new LeReader(ch, Math.min(32L * 1024 * 1024, fileSize));

			byte[] magic = r.readBytes(4);
			String m = new String(magic, StandardCharsets.US_ASCII);
			if (!"GGUF".equals(m)) {
				return ggufFile.length();
			}

			r.readI32();
			long tensorCount = r.readU64();
			long kvCount = r.readU64();

			long alignment = 32;

			for (long i = 0; i < kvCount; i++) {
				String key = r.readGgufString();
				int type = r.readI32();
				if ("general.alignment".equals(key)) {
					Object val = r.readGgufValue(type);
					if (val instanceof Number) {
						long a = ((Number) val).longValue();
						if (a > 0) {
							alignment = a;
						}
					}
				} else if ("tokenizer.ggml.tokens".equals(key) && type == 9) {
					int elemType = r.readI32();
					long len = r.readU64();
					for (long j = 0; j < len; j++) {
						r.skipGgufValue(elemType);
					}
				} else {
					r.skipGgufValue(type);
				}
			}

			long[] offsets = new long[(int) Math.min(tensorCount, Integer.MAX_VALUE)];
			int count = 0;
			for (long i = 0; i < tensorCount && count < offsets.length; i++) {
				r.readGgufString();
				int nDims = r.readI32();
				for (int d = 0; d < nDims; d++) {
					r.readU64();
				}
				r.readI32();
				long off = r.readU64();
				offsets[count++] = off;
			}

			long pos = r.position();
			long dataStart = alignUp(pos, alignment);
			long dataLen = fileSize - dataStart;
			if (dataLen <= 0 || count == 0) {
				return ggufFile.length();
			}

			Arrays.sort(offsets, 0, count);
			long sum = 0;
			for (int i = 0; i < count; i++) {
				long cur = offsets[i];
				long next = (i + 1 < count) ? offsets[i + 1] : dataLen;
				long size = next - cur;
				if (size > 0) {
					sum = safeAdd(sum, size);
				}
			}
			return sum;
		} catch (EOFException eof) {
			return ggufFile.length();
		}
	}

	private static long alignUp(long value, long alignment) {
		if (alignment <= 0) {
			return value;
		}
		long r = value % alignment;
		if (r == 0) {
			return value;
		}
		return value + (alignment - r);
	}

	private static long safeAdd(long a, long b) {
		long r = a + b;
		if (((a ^ r) & (b ^ r)) < 0) {
			return Long.MAX_VALUE;
		}
		return r;
	}

	private static String asString(Object o) {
		if (o == null) {
			return null;
		}
		if (o instanceof String s) {
			return s;
		}
		return String.valueOf(o);
	}

	private static String findBySuffix(Map<String, Object> meta, String suffix) {
		for (Map.Entry<String, Object> e : meta.entrySet()) {
			if (e.getKey() != null && e.getKey().endsWith(suffix)) {
				String s = asString(e.getValue());
				if (s != null && !s.isBlank()) {
					return s;
				}
			}
		}
		return null;
	}

	private static String findKeyBySuffix(Map<String, Object> meta, String suffix) {
		for (String k : meta.keySet()) {
			if (k != null && k.endsWith(suffix)) {
				return k;
			}
		}
		return null;
	}

	private static long firstLong(Map<String, Object> meta, String preferredKey, String fallbackKey) {
		Long a = asLong(meta.get(preferredKey));
		if (a != null && a > 0) {
			return a;
		}
		if (fallbackKey != null) {
			Long b = asLong(meta.get(fallbackKey));
			if (b != null && b > 0) {
				return b;
			}
		}
		return 0;
	}

	private static Long asLong(Object o) {
		if (o == null) {
			return null;
		}
		if (o instanceof Number n) {
			return n.longValue();
		}
		try {
			return Long.parseLong(String.valueOf(o));
		} catch (Exception e) {
			return null;
		}
	}

	private static final class LeReader {
		private final FileChannel ch;
		private final ByteBuffer buf;
		private long basePos;

		private LeReader(FileChannel ch, long initialWindowBytes) throws IOException {
			this.ch = ch;
			int cap = (int) Math.max(64 * 1024, Math.min(initialWindowBytes, 1024 * 1024));
			this.buf = ByteBuffer.allocate(cap);
			this.buf.order(ByteOrder.LITTLE_ENDIAN);
			this.basePos = 0;
			refill();
		}

		long position() {
			return basePos + buf.position();
		}

		private void ensure(int n) throws IOException {
			if (buf.remaining() >= n) {
				return;
			}
			compactAndRefill();
			if (buf.remaining() < n) {
				throw new EOFException();
			}
		}

		private void refill() throws IOException {
			buf.clear();
			int r = ch.read(buf, basePos);
			if (r <= 0) {
				buf.limit(0);
				return;
			}
			buf.flip();
		}

		private void compactAndRefill() throws IOException {
			basePos = basePos + buf.position();
			buf.compact();
			int writePos = buf.position();
			int r = ch.read(buf, basePos + writePos);
			if (r < 0) {
				r = 0;
			}
			buf.position(writePos + r);
			buf.flip();
		}

		byte[] readBytes(int n) throws IOException {
			ensure(n);
			byte[] out = new byte[n];
			buf.get(out);
			return out;
		}

		int readI32() throws IOException {
			ensure(4);
			return buf.getInt();
		}

		long readU64() throws IOException {
			ensure(8);
			return buf.getLong();
		}

		String readGgufString() throws IOException {
			long len = readU64();
			if (len < 0 || len > Integer.MAX_VALUE) {
				throw new IllegalArgumentException("GGUF字符串长度异常: " + len);
			}
			byte[] b = readBytes((int) len);
			return new String(b, StandardCharsets.UTF_8);
		}

		Object readGgufValue(int type) throws IOException {
			return switch (type) {
			case 0 -> readU8();
			case 1 -> readI8();
			case 2 -> readU16();
			case 3 -> readI16();
			case 4 -> readU32();
			case 5 -> readI32();
			case 6 -> readF32();
			case 7 -> readBool();
			case 8 -> readGgufString();
			case 9 -> readArray();
			case 10 -> readU64();
			case 11 -> readI64();
			case 12 -> readF64();
			default -> throw new IllegalArgumentException("未知GGUF value type: " + type);
			};
		}

		void skipGgufValue(int type) throws IOException {
			switch (type) {
			case 0:
				skip(1);
				return;
			case 1:
				skip(1);
				return;
			case 2:
				skip(2);
				return;
			case 3:
				skip(2);
				return;
			case 4:
				skip(4);
				return;
			case 5:
				skip(4);
				return;
			case 6:
				skip(4);
				return;
			case 7:
				skip(1);
				return;
			case 8: {
				long len = readU64();
				skip(len);
				return;
			}
			case 9: {
				int elemType = readI32();
				long len = readU64();
				for (long i = 0; i < len; i++) {
					skipGgufValue(elemType);
				}
				return;
			}
			case 10:
				skip(8);
				return;
			case 11:
				skip(8);
				return;
			case 12:
				skip(8);
				return;
			default:
				throw new IllegalArgumentException("未知GGUF value type: " + type);
			}
		}

		private void skip(long n) throws IOException {
			if (n <= 0) {
				return;
			}
			if (n > Integer.MAX_VALUE) {
				long target = position() + n;
				ch.position(target);
				basePos = target;
				refill();
				return;
			}
			int nn = (int) n;
			while (nn > 0) {
				int take = Math.min(nn, buf.remaining());
				if (take > 0) {
					buf.position(buf.position() + take);
					nn -= take;
					continue;
				}
				compactAndRefill();
				if (buf.remaining() == 0) {
					throw new EOFException();
				}
			}
		}

		private int readU8() throws IOException {
			ensure(1);
			return buf.get() & 0xFF;
		}

		private byte readI8() throws IOException {
			ensure(1);
			return buf.get();
		}

		private int readU16() throws IOException {
			ensure(2);
			return buf.getShort() & 0xFFFF;
		}

		private short readI16() throws IOException {
			ensure(2);
			return buf.getShort();
		}

		private long readU32() throws IOException {
			ensure(4);
			return buf.getInt() & 0xFFFFFFFFL;
		}

		private long readI64() throws IOException {
			ensure(8);
			return buf.getLong();
		}

		private float readF32() throws IOException {
			ensure(4);
			return buf.getFloat();
		}

		private boolean readBool() throws IOException {
			ensure(1);
			return buf.get() != 0;
		}

		private double readF64() throws IOException {
			ensure(8);
			return buf.getDouble();
		}

		private List<Object> readArray() throws IOException {
			int elemType = readI32();
			long len = readU64();
			if (len < 0 || len > Integer.MAX_VALUE) {
				for (long i = 0; i < len; i++) {
					skipGgufValue(elemType);
				}
				return List.of();
			}
			List<Object> out = new ArrayList<>((int) len);
			for (int i = 0; i < (int) len; i++) {
				out.add(readGgufValue(elemType));
			}
			return out;
		}
	}
}
