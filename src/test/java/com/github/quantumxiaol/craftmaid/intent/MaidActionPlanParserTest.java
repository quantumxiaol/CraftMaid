package com.github.quantumxiaol.craftmaid.intent;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MaidActionPlanParserTest {
  private final MaidActionPlanParser parser = new MaidActionPlanParser();

  @ParameterizedTest
  @ValueSource(
      strings = {
        "主人，我能帮你钓鱼。",
        "{}",
        "{\"chat\":\"你好\"}",
        "{\"chat\":\"你好\",\"actions\":{}}",
        "{\"chat\":42,\"actions\":[]}",
        "{\"chat\":\"\",\"actions\":[]}",
        "{\"chat\":\"\",\"actions\":[\"FOLLOW_START\"]}",
        "{\"chat\":\"\",\"actions\":[{\"type\":\"RUN_COMMAND\"}]}"
      })
  void malformedOrUnapprovedPlanCannotBecomeAnAction(String input) {
    assertFalse(parser.isValidPlan(input));
  }

  @Test
  void acceptsCompletePlansAndFencedJson() {
    assertTrue(parser.isValidPlan("{\"chat\":\"我在\",\"actions\":[]}"));
    String json = "{\"chat\":\"\",\"actions\":[{\"type\":\"FOLLOW_START\"}]}";
    assertTrue(parser.isValidPlan("```json\n" + json + "\n```"));
    assertEquals(
        MaidActionType.FOLLOW_START, parser.parse(json).orElseThrow().actions().getFirst().type());
  }

  @Test
  void finalAcceptsNaturalLanguageAndLegacyChatWithoutParsingActions() {
    String reply = "主人，前面是铁块墙和玻璃窗，只能看清这部分外墙。";
    assertEquals(reply, parser.parseFinalReply(reply).orElseThrow());
    assertEquals("[光钻] " + reply, parser.parseFinalReply("[光钻] " + reply).orElseThrow());
    assertEquals(
        "看见了",
        parser
            .parseFinalReply("{\"chat\":\"看见了\",\"actions\":[{\"type\":\"GUARD_START\"}]}")
            .orElseThrow());
    assertEquals("看见了", parser.parseFinalReply("```json\n{\"chat\":\"看见了\"}\n```").orElseThrow());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "{\"actions\":[{\"type\":\"RECALL\"}]}",
        "{\"reasoning_content\":\"private reasoning\"}",
        "{\"chat\":null}",
        "{\"chat\":\"broken",
        "<think>private reasoning</think>"
      })
  void finalDoesNotDisplayRawProtocolOrReasoning(String input) {
    assertTrue(parser.parseFinalReply(input).isEmpty());
  }
}
