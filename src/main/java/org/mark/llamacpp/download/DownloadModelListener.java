package org.mark.llamacpp.download;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.mark.llamacpp.download.BasicDownloader.DownloadProgress;
import org.mark.llamacpp.download.BasicDownloader.DownloadState;
import org.mark.llamacpp.server.LlamaServer;

/**
 * 	下载GGUF模型的监听器。
 */
public class DownloadModelListener implements DownloadProgressListener {
	
	
	public DownloadModelListener() {
		
	}
	

	@Override
	public void onStateChanged(DownloadTask task, DownloadState oldState, DownloadState newState) {
		
	}

	@Override
	public void onProgressUpdated(DownloadTask task, DownloadProgress progress) {
		
	}

	@Override
	public void onTaskCompleted(DownloadTask task) {
		System.err.println(task.getTargetPath());
		// 任务完成，判断目标是GGUF文件
		if(task.getFileName().toLowerCase().endsWith(".gguf")) {
			// 将文件转移到模型目录下。
			String name = task.getFileName();
			name = name.substring(0, name.lastIndexOf("."));
			//
			String defaultModelPath = LlamaServer.getDefaultModelsPath();
			// 在defaultModelPath目录下创建文件夹：name，再把task对应的文件剪切到name目录下。
			try {
				String sanitizedName = sanitizeDirectoryName(name);
				if (sanitizedName.isBlank()) {
					sanitizedName = "model";
				}
				Path modelDir = Paths.get(defaultModelPath).resolve(sanitizedName);
				Files.createDirectories(modelDir);

				Path source = task.getFullTargetPath();
				if (!Files.exists(source)) {
					return;
				}

				Path target = modelDir.resolve(source.getFileName().toString());
				Path sourceAbs = source.toAbsolutePath().normalize();
				Path targetAbs = target.toAbsolutePath().normalize();
				if (sourceAbs.equals(targetAbs)) {
					return;
				}

				try {
					Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				} catch (AtomicMoveNotSupportedException e) {
					Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException e) {
					Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
					Files.deleteIfExists(source);
				}
			} catch (Exception e) {
				System.err.println("移动模型文件失败: " + e.getMessage());
			}
		}
	}

	private static String sanitizeDirectoryName(String name) {
		if (name == null) {
			return "";
		}
		String cleaned = name.trim();
		if (cleaned.isEmpty()) {
			return cleaned;
		}
		cleaned = cleaned.replaceAll("[\\\\/:*?\"<>|]", "_");
		return cleaned;
	}

	@Override
	public void onTaskFailed(DownloadTask task, String error) {
		
	}

	@Override
	public void onTaskPaused(DownloadTask task) {
		
	}

	@Override
	public void onTaskResumed(DownloadTask task) {
		
	}
}
