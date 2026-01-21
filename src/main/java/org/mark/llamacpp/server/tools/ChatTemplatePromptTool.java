package org.mark.llamacpp.server.tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class ChatTemplatePromptTool {

	private ChatTemplatePromptTool() {
	}

	public static String buildPrompt(String templateName, String userContent) {
		JsonArray messages = new JsonArray();
		JsonObject msg = new JsonObject();
		msg.addProperty("role", "user");
		msg.addProperty("content", userContent == null ? "" : userContent);
		messages.add(msg);
		return buildPrompt(templateName, messages);
	}

	public static String buildPrompt(String templateName, JsonArray messages) {
		if (messages == null) return "";

		if (templateName != null && (templateName.contains("{%") || templateName.contains("{{"))) {
			return applyChatTemplate(templateName, messages, null, true, null);
		}

		BuiltinTemplate builtin = BuiltinTemplate.fromName(templateName);
		if (builtin == null) {
			return buildNaivePrompt(messages);
		}
		return switch (builtin) {
			case CHATML -> buildChatMlPrompt(messages);
			case LLAMA2 -> buildLlama2Prompt(messages);
		};
	}

	public static String applyChatTemplate(String chatTemplate, JsonArray messages) {
		return applyChatTemplate(chatTemplate, messages, null, true, null);
	}

	public static String applyChatTemplate(String chatTemplate, JsonArray messages, JsonArray tools, boolean addGenerationPrompt, Boolean enableThinking) {
		if (chatTemplate == null) return "";
		String tpl = chatTemplate;
		Map<String, Object> ctx = new HashMap<>();
		ctx.put("messages", jsonToJava(messages));
		ctx.put("tools", jsonToJava(tools));
		ctx.put("add_generation_prompt", addGenerationPrompt);
		if (enableThinking != null) {
			ctx.put("enable_thinking", enableThinking.booleanValue());
		}
		return new MiniJinjaEngine().render(tpl, ctx);
	}

	public static String buildChatMlPrompt(JsonArray messages) {
		if (messages == null) return "";

		StringBuilder sb = new StringBuilder();
		for (JsonElement el : messages) {
			if (el == null || el.isJsonNull() || !el.isJsonObject()) continue;
			JsonObject msg = el.getAsJsonObject();

			String role = safeLower(JsonUtil.getJsonString(msg, "role", "user"));
			String content = extractContentAsText(msg);

			sb.append("<|im_start|>").append(role).append("\n");
			if (!content.isEmpty()) sb.append(content);
			sb.append("<|im_end|>").append("\n");
		}
		sb.append("<|im_start|>assistant").append("\n");
		return sb.toString();
	}

	public static String buildLlama2Prompt(JsonArray messages) {
		if (messages == null) return "";

		String system = "";
		List<JsonObject> filtered = new ArrayList<>();
		for (JsonElement el : messages) {
			if (el == null || el.isJsonNull() || !el.isJsonObject()) continue;
			JsonObject msg = el.getAsJsonObject();
			String role = safeLower(JsonUtil.getJsonString(msg, "role", "user"));
			if ("system".equals(role)) {
				String c = extractContentAsText(msg);
				if (!c.isEmpty()) {
					if (!system.isEmpty()) system = system + "\n" + c;
					else system = c;
				}
				continue;
			}
			filtered.add(msg);
		}

		StringBuilder sb = new StringBuilder();
		boolean firstTurn = true;
		for (int i = 0; i < filtered.size(); i++) {
			JsonObject msg = filtered.get(i);
			String role = safeLower(JsonUtil.getJsonString(msg, "role", "user"));
			String content = extractContentAsText(msg);
			if (content.isEmpty()) continue;

			if ("assistant".equals(role)) {
				sb.append(" ").append(content).append(" </s>");
				firstTurn = false;
				continue;
			}

			if (!"user".equals(role)) {
				role = "user";
			}

			String sysPrefix = "";
			if (firstTurn && !system.isEmpty()) {
				sysPrefix = "<<SYS>>\n" + system + "\n<</SYS>>\n\n";
			}
			if (sb.length() == 0) sb.append("<s>");
			sb.append("[INST] ").append(sysPrefix).append(content).append(" [/INST]");
			firstTurn = false;
		}
		if (sb.length() == 0) {
			sb.append("<s>[INST] ");
			if (!system.isEmpty()) {
				sb.append("<<SYS>>\n").append(system).append("\n<</SYS>>\n\n");
			}
			sb.append("[/INST]");
		}
		sb.append(" ");
		return sb.toString();
	}

	public static String buildNaivePrompt(JsonArray messages) {
		StringBuilder sb = new StringBuilder();
		for (JsonElement el : messages) {
			if (el == null || el.isJsonNull() || !el.isJsonObject()) continue;
			JsonObject msg = el.getAsJsonObject();

			String role = safeLower(JsonUtil.getJsonString(msg, "role", "user"));
			String content = extractContentAsText(msg);

			if ("system".equals(role)) {
				sb.append("System:\n").append(content).append("\n\n");
			} else if ("user".equals(role)) {
				sb.append("User:\n").append(content).append("\n\n");
			} else if ("assistant".equals(role)) {
				sb.append("Assistant:\n").append(content).append("\n\n");
			} else {
				sb.append(role).append(":\n").append(content).append("\n\n");
			}
		}
		sb.append("Assistant:\n");
		return sb.toString();
	}

	private static String extractContentAsText(JsonObject msg) {
		if (msg == null) return "";
		if (!msg.has("content") || msg.get("content") == null || msg.get("content").isJsonNull()) return "";

		JsonElement contentEl = msg.get("content");
		if (contentEl.isJsonPrimitive()) {
			try {
				String s = contentEl.getAsString();
				return s == null ? "" : s;
			} catch (Exception e) {
				return "";
			}
		}

		if (contentEl.isJsonArray()) {
			StringBuilder sb = new StringBuilder();
			JsonArray arr = contentEl.getAsJsonArray();
			for (JsonElement partEl : arr) {
				if (partEl == null || partEl.isJsonNull()) continue;
				if (partEl.isJsonPrimitive()) {
					if (sb.length() > 0) sb.append("\n");
					sb.append(partEl.getAsString());
					continue;
				}
				if (!partEl.isJsonObject()) continue;
				JsonObject part = partEl.getAsJsonObject();
				String text = null;
				if (part.has("text") && part.get("text") != null && part.get("text").isJsonPrimitive()) {
					text = part.get("text").getAsString();
				} else if (part.has("content") && part.get("content") != null && part.get("content").isJsonPrimitive()) {
					text = part.get("content").getAsString();
				}
				if (text != null && !text.isEmpty()) {
					if (sb.length() > 0) sb.append("\n");
					sb.append(text);
				}
			}
			return sb.toString();
		}

		try {
			return contentEl.toString();
		} catch (Exception e) {
			return "";
		}
	}

	private static String safeLower(String s) {
		if (s == null) return "";
		try {
			return s.toLowerCase(Locale.ROOT);
		} catch (Exception e) {
			return s;
		}
	}

	private static Object jsonToJava(JsonElement el) {
		if (el == null || el.isJsonNull()) return null;
		if (el.isJsonPrimitive()) {
			try {
				if (el.getAsJsonPrimitive().isBoolean()) return el.getAsBoolean();
			} catch (Exception ignore) {
			}
			try {
				if (el.getAsJsonPrimitive().isNumber()) return el.getAsNumber();
			} catch (Exception ignore) {
			}
			try {
				return el.getAsString();
			} catch (Exception ignore) {
				return String.valueOf(el);
			}
		}
		if (el.isJsonArray()) {
			JsonArray arr = el.getAsJsonArray();
			List<Object> out = new ArrayList<>();
			for (JsonElement it : arr) {
				out.add(jsonToJava(it));
			}
			return out;
		}
		if (el.isJsonObject()) {
			JsonObject obj = el.getAsJsonObject();
			Map<String, Object> out = new HashMap<>();
			for (String k : obj.keySet()) {
				out.put(k, jsonToJava(obj.get(k)));
			}
			return out;
		}
		try {
			return el.toString();
		} catch (Exception e) {
			return "";
		}
	}

	private static final class MiniJinjaEngine {
		String render(String template, Map<String, Object> globals) {
			if (template == null) return "";
			List<TemplateToken> tokens = new TemplateLexer(template).lex();
			TemplateNode ast = new TemplateParser(tokens).parse();
			StringBuilder out = new StringBuilder();
			EvalScope scope = new EvalScope(null);
			if (globals != null) {
				for (Map.Entry<String, Object> e : globals.entrySet()) {
					scope.setLocal(e.getKey(), e.getValue());
				}
			}
			ast.render(out, scope);
			return out.toString();
		}
	}

	private enum TemplateTokenType {
		TEXT,
		EXPR,
		STMT
	}

	private static final class TemplateToken {
		final TemplateTokenType type;
		final String text;

		TemplateToken(TemplateTokenType type, String text) {
			this.type = type;
			this.text = text;
		}
	}

	private static final class TemplateLexer {
		private final String src;
		private int i;

		TemplateLexer(String src) {
			this.src = src == null ? "" : src;
		}

		List<TemplateToken> lex() {
			List<TemplateToken> out = new ArrayList<>();
			while (i < src.length()) {
				int exprPos = src.indexOf("{{", i);
				int stmtPos = src.indexOf("{%", i);
				int next = minPositive(exprPos, stmtPos);
				if (next < 0) {
					String tail = src.substring(i);
					if (!tail.isEmpty()) out.add(new TemplateToken(TemplateTokenType.TEXT, tail));
					i = src.length();
					break;
				}
				if (next > i) {
					out.add(new TemplateToken(TemplateTokenType.TEXT, src.substring(i, next)));
				}
				if (next == exprPos) {
					readTag(out, TemplateTokenType.EXPR, "{{", "}}");
				} else {
					readTag(out, TemplateTokenType.STMT, "{%", "%}");
				}
			}
			return mergeAdjacentText(out);
		}

		private void readTag(List<TemplateToken> out, TemplateTokenType type, String open, String close) {
			int start = i;
			boolean leftTrim = src.startsWith(open + "-", start);
			int openLen = leftTrim ? open.length() + 1 : open.length();
			i = start + openLen;

			int end = src.indexOf(close, i);
			if (end < 0) {
				out.add(new TemplateToken(TemplateTokenType.TEXT, src.substring(start)));
				i = src.length();
				return;
			}

			boolean rightTrim = end > i && src.charAt(end - 1) == '-';
			int contentEnd = rightTrim ? end - 1 : end;
			String content = src.substring(i, contentEnd).trim();

			if (leftTrim) {
				trimLastTextTokenRight(out);
			}
			out.add(new TemplateToken(type, content));

			i = end + close.length();
			if (rightTrim) {
				while (i < src.length()) {
					char c = src.charAt(i);
					if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
						i++;
						continue;
					}
					break;
				}
			}
		}

		private static int minPositive(int a, int b) {
			if (a < 0) return b;
			if (b < 0) return a;
			return Math.min(a, b);
		}

		private static void trimLastTextTokenRight(List<TemplateToken> out) {
			if (out.isEmpty()) return;
			TemplateToken last = out.get(out.size() - 1);
			if (last.type != TemplateTokenType.TEXT) return;
			String t = rstripWhitespace(last.text);
			out.set(out.size() - 1, new TemplateToken(TemplateTokenType.TEXT, t));
		}

		private static List<TemplateToken> mergeAdjacentText(List<TemplateToken> in) {
			List<TemplateToken> out = new ArrayList<>();
			for (TemplateToken t : in) {
				if (t.type == TemplateTokenType.TEXT && !out.isEmpty() && out.get(out.size() - 1).type == TemplateTokenType.TEXT) {
					TemplateToken prev = out.remove(out.size() - 1);
					out.add(new TemplateToken(TemplateTokenType.TEXT, prev.text + t.text));
				} else {
					out.add(t);
				}
			}
			return out;
		}

		private static String rstripWhitespace(String s) {
			if (s == null || s.isEmpty()) return "";
			int end = s.length();
			while (end > 0) {
				char c = s.charAt(end - 1);
				if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
					end--;
					continue;
				}
				break;
			}
			return s.substring(0, end);
		}
	}

	private interface TemplateNode {
		void render(StringBuilder out, EvalScope scope);
	}

	private static final class BlockNode implements TemplateNode {
		private final List<TemplateNode> children;

		BlockNode(List<TemplateNode> children) {
			this.children = children;
		}

		@Override
		public void render(StringBuilder out, EvalScope scope) {
			for (TemplateNode n : children) {
				n.render(out, scope);
			}
		}
	}

	private static final class TextNode implements TemplateNode {
		private final String text;

		TextNode(String text) {
			this.text = text == null ? "" : text;
		}

		@Override
		public void render(StringBuilder out, EvalScope scope) {
			out.append(text);
		}
	}

	private static final class ExprNode implements TemplateNode {
		private final String expr;

		ExprNode(String expr) {
			this.expr = expr;
		}

		@Override
		public void render(StringBuilder out, EvalScope scope) {
			Object v = new ExprParser(expr).parseExpression().eval(scope);
			if (v == Undefined.INSTANCE || v == null) return;
			out.append(stringify(v));
		}
	}

	private static final class SetNode implements TemplateNode {
		private final LValue target;
		private final Expr value;

		SetNode(LValue target, Expr value) {
			this.target = target;
			this.value = value;
		}

		@Override
		public void render(StringBuilder out, EvalScope scope) {
			Object v = value.eval(scope);
			target.assign(scope, v);
		}
	}

	private static final class IfNode implements TemplateNode {
		private final List<Branch> branches;
		private final TemplateNode elseBody;

		IfNode(List<Branch> branches, TemplateNode elseBody) {
			this.branches = branches;
			this.elseBody = elseBody;
		}

		@Override
		public void render(StringBuilder out, EvalScope scope) {
			for (Branch b : branches) {
				Object v = b.cond.eval(scope);
				if (isTruthy(v)) {
					b.body.render(out, scope);
					return;
				}
			}
			if (elseBody != null) elseBody.render(out, scope);
		}

		private static final class Branch {
			final Expr cond;
			final TemplateNode body;

			Branch(Expr cond, TemplateNode body) {
				this.cond = cond;
				this.body = body;
			}
		}
	}

	private static final class ForNode implements TemplateNode {
		private final String varName;
		private final Expr iterable;
		private final TemplateNode body;

		ForNode(String varName, Expr iterable, TemplateNode body) {
			this.varName = varName;
			this.iterable = iterable;
			this.body = body;
		}

		@Override
		public void render(StringBuilder out, EvalScope scope) {
			Object it = iterable.eval(scope);
			List<?> list = toList(it);
			if (list == null) return;

			for (int idx = 0; idx < list.size(); idx++) {
				Object item = list.get(idx);
				EvalScope child = new EvalScope(scope);
				child.setLocal(varName, item);
				child.setLocal("loop", new LoopVars(idx, list.size()));
				body.render(out, child);
			}
		}
	}

	private static final class TemplateParser {
		private final List<TemplateToken> tokens;
		private int i;

		TemplateParser(List<TemplateToken> tokens) {
			this.tokens = tokens == null ? List.of() : tokens;
		}

		TemplateNode parse() {
			return parseBlockUntil(null).node;
		}

		private ParseResult parseBlockUntil(EndPredicate end) {
			List<TemplateNode> children = new ArrayList<>();
			while (i < tokens.size()) {
				TemplateToken t = tokens.get(i);
				if (t.type == TemplateTokenType.STMT && end != null && end.isEnd(t.text)) {
					return new ParseResult(new BlockNode(children), t.text);
				}
				i++;
				if (t.type == TemplateTokenType.TEXT) {
					children.add(new TextNode(t.text));
				} else if (t.type == TemplateTokenType.EXPR) {
					children.add(new ExprNode(t.text));
				} else {
					children.add(parseStmtNode(t.text));
				}
			}
			return new ParseResult(new BlockNode(children), null);
		}

		private TemplateNode parseStmtNode(String stmt) {
			String s = stmt == null ? "" : stmt.trim();
			if (s.startsWith("if ")) {
				return parseIf(s.substring(3).trim());
			}
			if (s.startsWith("for ")) {
				return parseFor(s.substring(4).trim());
			}
			if (s.startsWith("set ")) {
				return parseSet(s.substring(4).trim());
			}
			return new TextNode("");
		}

		private TemplateNode parseIf(String firstCond) {
			List<IfNode.Branch> branches = new ArrayList<>();
			TemplateNode elseBody = null;

			Expr first = new ExprParser(firstCond).parseExpression();
			ParseResult body1 = parseBlockUntil(text -> text.startsWith("elif ") || text.equals("else") || text.equals("endif"));
			branches.add(new IfNode.Branch(first, body1.node));

			String endTag = body1.endTag;
			while (endTag != null && endTag.startsWith("elif ")) {
				i++;
				String cond = endTag.substring(5).trim();
				Expr c = new ExprParser(cond).parseExpression();
				ParseResult b = parseBlockUntil(text -> text.startsWith("elif ") || text.equals("else") || text.equals("endif"));
				branches.add(new IfNode.Branch(c, b.node));
				endTag = b.endTag;
			}

			if ("else".equals(endTag)) {
				i++;
				ParseResult b = parseBlockUntil(text -> text.equals("endif"));
				elseBody = b.node;
				endTag = b.endTag;
			}

			if ("endif".equals(endTag)) {
				i++;
			}
			return new IfNode(branches, elseBody);
		}

		private TemplateNode parseFor(String header) {
			String h = header == null ? "" : header.trim();
			int inPos = indexOfWord(h, "in");
			if (inPos < 0) {
				parseBlockUntil(text -> text.equals("endfor"));
				if (i < tokens.size()) i++;
				return new TextNode("");
			}
			String var = h.substring(0, inPos).trim();
			String expr = h.substring(inPos + 2).trim();

			Expr iterable = new ExprParser(expr).parseExpression();
			ParseResult body = parseBlockUntil(text -> text.equals("endfor"));
			if ("endfor".equals(body.endTag)) i++;
			return new ForNode(var, iterable, body.node);
		}

		private TemplateNode parseSet(String stmt) {
			int eq = stmt.indexOf('=');
			if (eq < 0) return new TextNode("");
			String left = stmt.substring(0, eq).trim();
			String right = stmt.substring(eq + 1).trim();
			LValue target = LValue.parse(left);
			Expr value = new ExprParser(right).parseExpression();
			return new SetNode(target, value);
		}

		private static int indexOfWord(String s, String word) {
			if (s == null) return -1;
			String w = " " + word + " ";
			int idx = s.indexOf(w);
			if (idx >= 0) return idx + 1;
			if (s.startsWith(word + " ")) return 0;
			return -1;
		}

		private interface EndPredicate {
			boolean isEnd(String stmtText);
		}

		private static final class ParseResult {
			final TemplateNode node;
			final String endTag;

			ParseResult(TemplateNode node, String endTag) {
				this.node = node;
				this.endTag = endTag;
			}
		}
	}

	private static final class EvalScope {
		private final EvalScope parent;
		private final Map<String, Object> vars = new HashMap<>();

		EvalScope(EvalScope parent) {
			this.parent = parent;
		}

		Object get(String name) {
			if (vars.containsKey(name)) return vars.get(name);
			if (parent != null) return parent.get(name);
			return Undefined.INSTANCE;
		}

		boolean has(String name) {
			if (vars.containsKey(name)) return true;
			return parent != null && parent.has(name);
		}

		void setLocal(String name, Object value) {
			vars.put(name, value);
		}
	}

	private static final class LoopVars {
		final int index0;
		final int length;

		LoopVars(int index0, int length) {
			this.index0 = index0;
			this.length = length;
		}

		int index() {
			return index0 + 1;
		}

		boolean first() {
			return index0 == 0;
		}

		boolean last() {
			return index0 == length - 1;
		}
	}

	private enum Undefined {
		INSTANCE
	}

	private interface Expr {
		Object eval(EvalScope scope);
	}

	private interface LValue {
		void assign(EvalScope scope, Object value);

		static LValue parse(String s) {
			String t = s == null ? "" : s.trim();
			if (t.isEmpty()) return (scope, value) -> {};
			String[] parts = t.split("\\.");
			if (parts.length == 1) {
				return (scope, value) -> scope.setLocal(parts[0], value);
			}
			return new DotLValue(parts);
		}
	}

	private static final class DotLValue implements LValue {
		private final String[] parts;

		DotLValue(String[] parts) {
			this.parts = parts;
		}

		@Override
		public void assign(EvalScope scope, Object value) {
			if (parts.length == 0) return;
			Object base = scope.get(parts[0]);
			if (base == Undefined.INSTANCE) {
				base = new Namespace();
				scope.setLocal(parts[0], base);
			}
			Object cur = base;
			for (int i = 1; i < parts.length - 1; i++) {
				Object next = getAttr(cur, parts[i]);
				if (next == Undefined.INSTANCE || next == null) {
					Namespace ns = new Namespace();
					setAttr(cur, parts[i], ns);
					cur = ns;
				} else {
					cur = next;
				}
			}
			setAttr(cur, parts[parts.length - 1], value);
		}
	}

	private static final class Namespace extends HashMap<String, Object> {
		private static final long serialVersionUID = 1L;
	}

	private static final class ExprParser {
		private final ExprTokenizer tz;

		ExprParser(String src) {
			this.tz = new ExprTokenizer(src);
		}

		Expr parseExpression() {
			return parseTernary();
		}

		private Expr parseTernary() {
			Expr thenExpr = parseOr();
			if (tz.peekIsWord("if")) {
				tz.next();
				Expr cond = parseOr();
				tz.expectWord("else");
				Expr elseExpr = parseTernary();
				return scope -> isTruthy(cond.eval(scope)) ? thenExpr.eval(scope) : elseExpr.eval(scope);
			}
			return thenExpr;
		}

		private Expr parseOr() {
			Expr left = parseAnd();
			while (tz.peekIsWord("or")) {
				tz.next();
				Expr right = parseAnd();
				left = scope -> isTruthy(left.eval(scope)) ? Boolean.TRUE : (isTruthy(right.eval(scope)) ? Boolean.TRUE : Boolean.FALSE);
			}
			return left;
		}

		private Expr parseAnd() {
			Expr left = parseNot();
			while (tz.peekIsWord("and")) {
				tz.next();
				Expr right = parseNot();
				left = scope -> isTruthy(left.eval(scope)) && isTruthy(right.eval(scope)) ? Boolean.TRUE : Boolean.FALSE;
			}
			return left;
		}

		private Expr parseNot() {
			if (tz.peekIsWord("not")) {
				tz.next();
				Expr inner = parseNot();
				return scope -> isTruthy(inner.eval(scope)) ? Boolean.FALSE : Boolean.TRUE;
			}
			return parseCompare();
		}

		private Expr parseCompare() {
			Expr left = parseAdd();
			while (true) {
				if (tz.peekIsWord("in")) {
					tz.next();
					Expr right = parseAdd();
					left = scope -> contains(right.eval(scope), left.eval(scope)) ? Boolean.TRUE : Boolean.FALSE;
					continue;
				}
				if (tz.peekIsSymbol("==") || tz.peekIsSymbol("!=") || tz.peekIsSymbol("<") || tz.peekIsSymbol(">") || tz.peekIsSymbol("<=") || tz.peekIsSymbol(">=")) {
					String op = tz.next().text;
					Expr right = parseAdd();
					left = scope -> compare(op, left.eval(scope), right.eval(scope)) ? Boolean.TRUE : Boolean.FALSE;
					continue;
				}
				if (tz.peekIsWord("is")) {
					tz.next();
					boolean neg = false;
					if (tz.peekIsWord("not")) {
						tz.next();
						neg = true;
					}
					if (tz.peekIsWord("defined")) {
						tz.next();
						Expr base = left;
						boolean n = neg;
						left = scope -> {
							Object v = base.eval(scope);
							boolean defined = v != Undefined.INSTANCE;
							return (defined ^ n) ? Boolean.TRUE : Boolean.FALSE;
						};
						continue;
					}
					if (tz.peekIsWord("none")) {
						tz.next();
						Expr base = left;
						boolean n = neg;
						left = scope -> {
							Object v = base.eval(scope);
							boolean isNone = v == null;
							return (isNone ^ n) ? Boolean.TRUE : Boolean.FALSE;
						};
						continue;
					}
					if (tz.peekIsWord("string")) {
						tz.next();
						Expr base = left;
						boolean n = neg;
						left = scope -> {
							Object v = base.eval(scope);
							boolean isStr = v instanceof String;
							return (isStr ^ n) ? Boolean.TRUE : Boolean.FALSE;
						};
						continue;
					}
				}
				break;
			}
			return left;
		}

		private Expr parseAdd() {
			Expr left = parseUnary();
			while (tz.peekIsSymbol("+") || tz.peekIsSymbol("-")) {
				String op = tz.next().text;
				Expr right = parseUnary();
				if (op.equals("+")) {
					left = scope -> add(left.eval(scope), right.eval(scope));
				} else {
					left = scope -> subtract(left.eval(scope), right.eval(scope));
				}
			}
			return left;
		}

		private Expr parseUnary() {
			if (tz.peekIsSymbol("-")) {
				tz.next();
				Expr inner = parseUnary();
				return scope -> negate(inner.eval(scope));
			}
			return parsePostfix();
		}

		private Expr parsePostfix() {
			Expr base = parsePrimary();
			while (true) {
				if (tz.peekIsSymbol(".")) {
					tz.next();
					ExprToken name = tz.next();
					String attr = name == null ? "" : name.text;
					Expr prev = base;
					base = scope -> getAttr(prev.eval(scope), attr);
					continue;
				}
				if (tz.peekIsSymbol("[")) {
					tz.next();
					if (tz.peekIsSymbol(":")) {
						tz.next();
						Expr end = tz.peekIsSymbol("]") ? (s -> Undefined.INSTANCE) : parseExpression();
						tz.expect("]");
						Expr prev = base;
						base = scope -> slice(prev.eval(scope), null, end.eval(scope));
						continue;
					}
					Expr start = tz.peekIsSymbol("]") ? (s -> Undefined.INSTANCE) : parseExpression();
					if (tz.peekIsSymbol(":")) {
						tz.next();
						Expr end = tz.peekIsSymbol("]") ? (s -> Undefined.INSTANCE) : parseExpression();
						tz.expect("]");
						Expr prev = base;
						base = scope -> slice(prev.eval(scope), start.eval(scope), end.eval(scope));
						continue;
					}
					tz.expect("]");
					Expr prev = base;
					base = scope -> index(prev.eval(scope), start.eval(scope));
					continue;
				}
				if (tz.peekIsSymbol("(")) {
					tz.next();
					List<Expr> args = new ArrayList<>();
					Map<String, Expr> kwargs = new HashMap<>();
					if (!tz.peekIsSymbol(")")) {
						while (true) {
							if (tz.peekType(ExprTokenType.IDENT) && tz.peekAheadIsSymbol(1, "=")) {
								String k = tz.next().text;
								tz.expect("=");
								Expr v = parseExpression();
								kwargs.put(k, v);
							} else {
								args.add(parseExpression());
							}
							if (tz.peekIsSymbol(",")) {
								tz.next();
								continue;
							}
							break;
						}
					}
					tz.expect(")");
					Expr prev = base;
					base = scope -> call(prev.eval(scope), args, kwargs, scope);
					continue;
				}
				if (tz.peekIsSymbol("|")) {
					tz.next();
					String filter = tz.next().text;
					List<Expr> args = new ArrayList<>();
					if (tz.peekIsSymbol("(")) {
						tz.next();
						if (!tz.peekIsSymbol(")")) {
							while (true) {
								args.add(parseExpression());
								if (tz.peekIsSymbol(",")) {
									tz.next();
									continue;
								}
								break;
							}
						}
						tz.expect(")");
					}
					Expr prev = base;
					base = scope -> applyFilter(filter, prev.eval(scope), args, scope);
					continue;
				}
				break;
			}
			return base;
		}

		private Expr parsePrimary() {
			if (tz.peekIsSymbol("(")) {
				tz.next();
				Expr e = parseExpression();
				tz.expect(")");
				return e;
			}
			ExprToken t = tz.next();
			if (t == null) return scope -> Undefined.INSTANCE;
			if (t.type == ExprTokenType.STRING) {
				String v = unescapeString(t.text);
				return scope -> v;
			}
			if (t.type == ExprTokenType.NUMBER) {
				String n = t.text;
				return scope -> {
					try {
						if (n.contains(".")) return Double.valueOf(n);
						return Integer.valueOf(n);
					} catch (Exception e) {
						return 0;
					}
				};
			}
			if (t.type == ExprTokenType.IDENT) {
				String id = t.text;
				if ("true".equals(id)) return scope -> Boolean.TRUE;
				if ("false".equals(id)) return scope -> Boolean.FALSE;
				if ("none".equals(id) || "null".equals(id)) return scope -> null;
				if ("namespace".equals(id)) {
					return scope -> (MethodRef) (args, kwargs, sc) -> {
						Namespace ns = new Namespace();
						if (kwargs != null) {
							for (Map.Entry<String, Expr> e : kwargs.entrySet()) {
								ns.put(e.getKey(), e.getValue().eval(sc));
							}
						}
						return ns;
					};
				}
				return scope -> scope.get(id);
			}
			return scope -> Undefined.INSTANCE;
		}

		private static String unescapeString(String raw) {
			if (raw == null || raw.length() < 2) return raw == null ? "" : raw;
			char quote = raw.charAt(0);
			String inner = raw.substring(1, raw.length() - 1);
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < inner.length(); i++) {
				char c = inner.charAt(i);
				if (c == '\\' && i + 1 < inner.length()) {
					char n = inner.charAt(i + 1);
					if (n == 'n') {
						sb.append('\n');
						i++;
						continue;
					}
					if (n == 'r') {
						sb.append('\r');
						i++;
						continue;
					}
					if (n == 't') {
						sb.append('\t');
						i++;
						continue;
					}
					if (n == quote) {
						sb.append(quote);
						i++;
						continue;
					}
					sb.append(n);
					i++;
					continue;
				}
				sb.append(c);
			}
			return sb.toString();
		}
	}

	private enum ExprTokenType {
		IDENT,
		NUMBER,
		STRING,
		SYMBOL
	}

	private static final class ExprToken {
		final ExprTokenType type;
		final String text;

		ExprToken(ExprTokenType type, String text) {
			this.type = type;
			this.text = text;
		}
	}

	private static final class ExprTokenizer {
		private final String src;
		private int i;
		private ExprToken buffered;

		ExprTokenizer(String src) {
			this.src = src == null ? "" : src;
		}

		ExprToken next() {
			if (buffered != null) {
				ExprToken t = buffered;
				buffered = null;
				return t;
			}
			skipWs();
			if (i >= src.length()) return null;
			char c = src.charAt(i);
			if (isIdentStart(c)) {
				int start = i++;
				while (i < src.length() && isIdentPart(src.charAt(i))) i++;
				return new ExprToken(ExprTokenType.IDENT, src.substring(start, i));
			}
			if (Character.isDigit(c)) {
				int start = i++;
				while (i < src.length()) {
					char n = src.charAt(i);
					if (Character.isDigit(n) || n == '.') {
						i++;
						continue;
					}
					break;
				}
				return new ExprToken(ExprTokenType.NUMBER, src.substring(start, i));
			}
			if (c == '\'' || c == '"') {
				char q = c;
				int start = i++;
				while (i < src.length()) {
					char n = src.charAt(i);
					if (n == '\\') {
						i += 2;
						continue;
					}
					if (n == q) {
						i++;
						break;
					}
					i++;
				}
				return new ExprToken(ExprTokenType.STRING, src.substring(start, i));
			}
			String two = i + 1 < src.length() ? src.substring(i, i + 2) : "";
			if (two.equals("==") || two.equals("!=") || two.equals("<=") || two.equals(">=")) {
				i += 2;
				return new ExprToken(ExprTokenType.SYMBOL, two);
			}
			i++;
			return new ExprToken(ExprTokenType.SYMBOL, String.valueOf(c));
		}

		boolean peekIsWord(String w) {
			ExprToken t = peek();
			return t != null && t.type == ExprTokenType.IDENT && w.equals(t.text);
		}

		boolean peekIsSymbol(String s) {
			ExprToken t = peek();
			return t != null && t.type == ExprTokenType.SYMBOL && s.equals(t.text);
		}

		boolean peekType(ExprTokenType type) {
			ExprToken t = peek();
			return t != null && t.type == type;
		}

		boolean peekAheadIsSymbol(int offset, String sym) {
			ExprToken t = peekAhead(offset);
			return t != null && t.type == ExprTokenType.SYMBOL && sym.equals(t.text);
		}

		ExprToken peek() {
			if (buffered == null) buffered = next();
			return buffered;
		}

		ExprToken peekAhead(int offset) {
			State st = snapshot();
			ExprToken target = null;
			for (int k = 0; k <= offset; k++) {
				ExprToken t = next();
				if (t == null) break;
				if (k == offset) target = t;
			}
			restore(st);
			return target;
		}

		void expect(String sym) {
			ExprToken t = next();
			if (t == null || t.type != ExprTokenType.SYMBOL || !sym.equals(t.text)) {
				throw new IllegalStateException("Expected " + sym);
			}
		}

		void expectWord(String word) {
			ExprToken t = next();
			if (t == null || t.type != ExprTokenType.IDENT || !word.equals(t.text)) {
				throw new IllegalStateException("Expected " + word);
			}
		}

		private State snapshot() {
			return new State(i, buffered);
		}

		private void restore(State st) {
			this.i = st.i;
			this.buffered = st.buffered;
		}

		private static final class State {
			final int i;
			final ExprToken buffered;

			State(int i, ExprToken buffered) {
				this.i = i;
				this.buffered = buffered;
			}
		}

		private void skipWs() {
			while (i < src.length()) {
				char c = src.charAt(i);
				if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
					i++;
					continue;
				}
				break;
			}
		}

		private static boolean isIdentStart(char c) {
			return Character.isLetter(c) || c == '_' ;
		}

		private static boolean isIdentPart(char c) {
			return Character.isLetterOrDigit(c) || c == '_' ;
		}
	}

	private static Object call(Object callee, List<Expr> args, Map<String, Expr> kwargs, EvalScope scope) {
		if (callee == Undefined.INSTANCE) return Undefined.INSTANCE;
		if (callee instanceof MethodRef mr) {
			return mr.invoke(args, kwargs, scope);
		}
		return Undefined.INSTANCE;
	}

	private static Object applyFilter(String name, Object value, List<Expr> args, EvalScope scope) {
		if (name == null) return value;
		String n = name.trim();
		if (n.equals("length")) {
			return lengthOf(value);
		}
		if (n.equals("tojson")) {
			if (value == Undefined.INSTANCE) return "";
			return JsonUtil.toJson(value);
		}
		if (n.equals("first")) {
			List<?> list = toList(value);
			if (list == null || list.isEmpty()) return Undefined.INSTANCE;
			return list.get(0);
		}
		if (n.equals("last")) {
			List<?> list = toList(value);
			if (list == null || list.isEmpty()) return Undefined.INSTANCE;
			return list.get(list.size() - 1);
		}
		if (n.equals("trim") || n.equals("strip")) {
			String s = stringify(value);
			String chars = args != null && !args.isEmpty() ? stringify(args.get(0).eval(scope)) : null;
			return strip(s, chars, true, true);
		}
		if (n.equals("lstrip")) {
			String s = stringify(value);
			String chars = args != null && !args.isEmpty() ? stringify(args.get(0).eval(scope)) : null;
			return strip(s, chars, true, false);
		}
		if (n.equals("rstrip")) {
			String s = stringify(value);
			String chars = args != null && !args.isEmpty() ? stringify(args.get(0).eval(scope)) : null;
			return strip(s, chars, false, true);
		}
		return value;
	}

	private interface MethodRef {
		Object invoke(List<Expr> args, Map<String, Expr> kwargs, EvalScope scope);
	}

	private static Object getAttr(Object obj, String attr) {
		if (obj == null || obj == Undefined.INSTANCE) return Undefined.INSTANCE;
		if (obj instanceof Map<?, ?> m) {
			if (m.containsKey(attr)) return m.get(attr);
			return Undefined.INSTANCE;
		}
		if (obj instanceof Namespace ns) {
			if (ns.containsKey(attr)) return ns.get(attr);
			return Undefined.INSTANCE;
		}
		if (obj instanceof LoopVars lv) {
			return switch (attr) {
				case "index0" -> lv.index0;
				case "index" -> lv.index();
				case "first" -> lv.first();
				case "last" -> lv.last();
				default -> Undefined.INSTANCE;
			};
		}
		if (obj instanceof String s) {
			if ("split".equals(attr)) {
				return (MethodRef) (args, kwargs, scope) -> {
					String sep = args != null && !args.isEmpty() ? stringify(args.get(0).eval(scope)) : "";
					if (sep.isEmpty()) return Arrays.asList(s.split("", -1));
					return splitLiteral(s, sep);
				};
			}
			if ("lstrip".equals(attr) || "rstrip".equals(attr) || "strip".equals(attr)) {
				String kind = attr;
				return (MethodRef) (args, kwargs, scope) -> {
					String chars = args != null && !args.isEmpty() ? stringify(args.get(0).eval(scope)) : null;
					boolean left = !kind.equals("rstrip");
					boolean right = !kind.equals("lstrip");
					return strip(s, chars, left, right);
				};
			}
		}
		return Undefined.INSTANCE;
	}

	private static void setAttr(Object obj, String attr, Object value) {
		if (obj == null || obj == Undefined.INSTANCE) return;
		if (obj instanceof Map<?, ?> m) {
			@SuppressWarnings("unchecked")
			Map<String, Object> mm = (Map<String, Object>) m;
			mm.put(attr, value);
			return;
		}
	}

	private static Object index(Object base, Object idx) {
		if (base == null || base == Undefined.INSTANCE) return Undefined.INSTANCE;
		if (idx == null || idx == Undefined.INSTANCE) return Undefined.INSTANCE;
		if (base instanceof List<?> list) {
			Integer i = toInt(idx);
			if (i == null) return Undefined.INSTANCE;
			int pos = i;
			if (pos < 0) pos = list.size() + pos;
			if (pos < 0 || pos >= list.size()) return Undefined.INSTANCE;
			return list.get(pos);
		}
		if (base instanceof Map<?, ?> map) {
			String key = stringify(idx);
			if (map.containsKey(key)) return map.get(key);
			return Undefined.INSTANCE;
		}
		if (base instanceof String s) {
			Integer i = toInt(idx);
			if (i == null) return Undefined.INSTANCE;
			int pos = i;
			if (pos < 0) pos = s.length() + pos;
			if (pos < 0 || pos >= s.length()) return Undefined.INSTANCE;
			return String.valueOf(s.charAt(pos));
		}
		return Undefined.INSTANCE;
	}

	private static Object slice(Object base, Object start, Object end) {
		if (base == null || base == Undefined.INSTANCE) return Undefined.INSTANCE;
		Integer s = start == null || start == Undefined.INSTANCE ? null : toInt(start);
		Integer e = end == null || end == Undefined.INSTANCE ? null : toInt(end);
		if (base instanceof String str) {
			int len = str.length();
			int from = s == null ? 0 : clampIndex(s, len);
			int to = e == null ? len : clampIndex(e, len);
			if (to < from) to = from;
			return str.substring(from, to);
		}
		if (base instanceof List<?> list) {
			int len = list.size();
			int from = s == null ? 0 : clampIndex(s, len);
			int to = e == null ? len : clampIndex(e, len);
			if (to < from) to = from;
			return new ArrayList<>(list.subList(from, to));
		}
		return Undefined.INSTANCE;
	}

	private static int clampIndex(int idx, int len) {
		int i = idx;
		if (i < 0) i = len + i;
		if (i < 0) i = 0;
		if (i > len) i = len;
		return i;
	}

	private static boolean contains(Object container, Object item) {
		if (container == null || container == Undefined.INSTANCE) return false;
		if (item == null || item == Undefined.INSTANCE) return false;
		if (container instanceof String s) {
			return s.contains(stringify(item));
		}
		if (container instanceof List<?> list) {
			for (Object it : list) {
				if (Objects.equals(it, item)) return true;
				if (Objects.equals(stringify(it), stringify(item))) return true;
			}
			return false;
		}
		if (container instanceof Map<?, ?> map) {
			return map.containsKey(stringify(item));
		}
		return false;
	}

	private static boolean compare(String op, Object a, Object b) {
		if (op == null) return false;
		if (op.equals("==")) return equalsLoose(a, b);
		if (op.equals("!=")) return !equalsLoose(a, b);
		Double da = toDouble(a);
		Double db = toDouble(b);
		if (da != null && db != null) {
			return switch (op) {
				case "<" -> da < db;
				case ">" -> da > db;
				case "<=" -> da <= db;
				case ">=" -> da >= db;
				default -> false;
			};
		}
		String sa = stringify(a);
		String sb = stringify(b);
		int cmp = sa.compareTo(sb);
		return switch (op) {
			case "<" -> cmp < 0;
			case ">" -> cmp > 0;
			case "<=" -> cmp <= 0;
			case ">=" -> cmp >= 0;
			default -> false;
		};
	}

	private static boolean equalsLoose(Object a, Object b) {
		if (a == Undefined.INSTANCE) a = null;
		if (b == Undefined.INSTANCE) b = null;
		if (Objects.equals(a, b)) return true;
		Double da = toDouble(a);
		Double db = toDouble(b);
		if (da != null && db != null) return da.doubleValue() == db.doubleValue();
		return Objects.equals(stringify(a), stringify(b));
	}

	private static Object add(Object a, Object b) {
		if (a == Undefined.INSTANCE) a = null;
		if (b == Undefined.INSTANCE) b = null;
		if (a instanceof String || b instanceof String) {
			return stringify(a) + stringify(b);
		}
		Double da = toDouble(a);
		Double db = toDouble(b);
		if (da != null && db != null) return da + db;
		return stringify(a) + stringify(b);
	}

	private static Object subtract(Object a, Object b) {
		Double da = toDouble(a);
		Double db = toDouble(b);
		if (da != null && db != null) return da - db;
		return 0;
	}

	private static Object negate(Object a) {
		Double da = toDouble(a);
		if (da != null) return -da;
		return 0;
	}

	private static Integer toInt(Object v) {
		if (v == null || v == Undefined.INSTANCE) return null;
		if (v instanceof Number n) return n.intValue();
		try {
			return Integer.parseInt(String.valueOf(v).trim());
		} catch (Exception e) {
			return null;
		}
	}

	private static Double toDouble(Object v) {
		if (v == null || v == Undefined.INSTANCE) return null;
		if (v instanceof Number n) return n.doubleValue();
		try {
			return Double.parseDouble(String.valueOf(v).trim());
		} catch (Exception e) {
			return null;
		}
	}

	private static boolean isTruthy(Object v) {
		if (v == null || v == Undefined.INSTANCE) return false;
		if (v instanceof Boolean b) return b.booleanValue();
		if (v instanceof Number n) return n.doubleValue() != 0.0;
		if (v instanceof String s) return !s.isEmpty();
		if (v instanceof List<?> l) return !l.isEmpty();
		if (v instanceof Map<?, ?> m) return !m.isEmpty();
		return true;
	}

	private static int lengthOf(Object v) {
		if (v == null || v == Undefined.INSTANCE) return 0;
		if (v instanceof String s) return s.length();
		if (v instanceof List<?> l) return l.size();
		if (v instanceof Map<?, ?> m) return m.size();
		return stringify(v).length();
	}

	private static List<?> toList(Object v) {
		if (v == null || v == Undefined.INSTANCE) return null;
		if (v instanceof List<?> l) return l;
		if (v instanceof Object[] arr) return Arrays.asList(arr);
		return null;
	}

	private static String stringify(Object v) {
		if (v == null || v == Undefined.INSTANCE) return "";
		return String.valueOf(v);
	}

	private static List<String> splitLiteral(String s, String sep) {
		if (sep == null) sep = "";
		if (sep.isEmpty()) return Arrays.asList(s.split("", -1));
		List<String> out = new ArrayList<>();
		int pos = 0;
		while (true) {
			int idx = s.indexOf(sep, pos);
			if (idx < 0) {
				out.add(s.substring(pos));
				break;
			}
			out.add(s.substring(pos, idx));
			pos = idx + sep.length();
		}
		return out;
	}

	private static String strip(String s, String chars, boolean left, boolean right) {
		if (s == null) return "";
		if (chars == null) {
			if (left) s = lstripWs(s);
			if (right) s = rstripWs(s);
			return s;
		}
		String set = chars;
		int start = 0;
		int end = s.length();
		if (left) {
			while (start < end) {
				char c = s.charAt(start);
				if (set.indexOf(c) >= 0) start++;
				else break;
			}
		}
		if (right) {
			while (end > start) {
				char c = s.charAt(end - 1);
				if (set.indexOf(c) >= 0) end--;
				else break;
			}
		}
		return s.substring(start, end);
	}

	private static String lstripWs(String s) {
		int start = 0;
		while (start < s.length()) {
			char c = s.charAt(start);
			if (c == ' ' || c == '\t' || c == '\r' || c == '\n') start++;
			else break;
		}
		return s.substring(start);
	}

	private static String rstripWs(String s) {
		int end = s.length();
		while (end > 0) {
			char c = s.charAt(end - 1);
			if (c == ' ' || c == '\t' || c == '\r' || c == '\n') end--;
			else break;
		}
		return s.substring(0, end);
	}

	private enum BuiltinTemplate {
		CHATML("chatml"),
		LLAMA2("llama2");

		private final String name;

		BuiltinTemplate(String name) {
			this.name = name;
		}

		static BuiltinTemplate fromName(String name) {
			if (name == null) return null;
			String n = name.trim().toLowerCase(Locale.ROOT);
			if (n.isEmpty()) return null;
			for (BuiltinTemplate t : values()) {
				if (t.name.equals(n)) return t;
			}
			return null;
		}
	}
}
