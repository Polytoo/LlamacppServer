package org.mark.llamacpp.server.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class ChatTemplatePromptToolTest {

	public static void main(String[] args) {
		testChatMlSingleTurn();
		testNaiveFallback();
		System.out.println("ChatTemplatePromptToolTest OK");
	}

	private static void testChatMlSingleTurn() {
		JsonArray messages = new JsonArray();
		JsonObject msg = new JsonObject();
		msg.addProperty("role", "user");
		msg.addProperty("content", "你在干什么？");
		messages.add(msg);

		String template = ""
				+ "{%- for message in messages -%}\n"
				+ "{{- '<|im_start|>' + message.role + '\\n' + (message.content if message.content is defined and message.content is not none else '') + '<|im_end|>\\n' -}}\n"
				+ "{%- endfor -%}\n"
				+ "{%- if add_generation_prompt -%}\n"
				+ "{{- '<|im_start|>assistant\\n' -}}\n"
				+ "{%- endif -%}\n";
		String out = ChatTemplatePromptTool.applyChatTemplate(template, messages, null, true, null);
		String expected = "<|im_start|>user\n你在干什么？<|im_end|>\n<|im_start|>assistant\n";
		assertEquals(expected, out);
	}

	private static void testNaiveFallback() {
		JsonArray messages = new JsonArray();
		JsonObject msg = new JsonObject();
		msg.addProperty("role", "user");
		msg.addProperty("content", "hi");
		messages.add(msg);

		String out = ChatTemplatePromptTool.buildPrompt("unknown-template", messages);
		if (out == null || !out.contains("User:\nhi")) {
			throw new IllegalStateException("naive fallback failed: " + out);
		}
	}

	private static void assertEquals(String expected, String actual) {
		if (expected == null && actual == null) return;
		if (expected != null && expected.equals(actual)) return;
		throw new IllegalStateException("assertEquals failed\nexpected:\n" + expected + "\nactual:\n" + actual);
	}
}
