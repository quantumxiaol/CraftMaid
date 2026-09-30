package com.github.quantumxiaol.craftmaid.intent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MaidActionPlanParser {
  public Optional<MaidActionPlan> parse(String raw) {
    String json = extractJsonObject(raw);
    if (json.isBlank()) {
      return Optional.empty();
    }

    try {
      JsonObject root = JsonParser.parseString(json).getAsJsonObject();
      if (!root.has("chat")
          || !root.get("chat").isJsonPrimitive()
          || !root.getAsJsonPrimitive("chat").isString()
          || !root.has("actions")
          || !root.get("actions").isJsonArray()) {
        return Optional.empty();
      }
      String chat = stringOrBlank(root, "chat");
      List<MaidAction> actions = parseActions(root.get("actions"));
      return Optional.of(new MaidActionPlan(chat, actions));
    } catch (RuntimeException ex) {
      return Optional.empty();
    }
  }

  public boolean isValidPlan(String raw) {
    return parse(raw).filter(plan -> plan.hasActions() || !plan.chat().isBlank()).isPresent();
  }

  /** FINAL is display-only. A legacy JSON envelope is tolerated but can never execute actions. */
  public Optional<String> parseFinalReply(String raw) {
    if (raw == null || raw.isBlank()) return Optional.empty();
    String text = raw.trim();
    if (text.startsWith("<think>") || text.startsWith("<analysis>")) return Optional.empty();
    boolean looksLikeArray = text.matches("(?s)^\\[\\s*[\\{\\[\\]\"].*");
    if (text.startsWith("{") || looksLikeArray || text.startsWith("```")) {
      try {
        JsonObject root = JsonParser.parseString(extractJsonObject(text)).getAsJsonObject();
        JsonElement chat = root.get("chat");
        if (chat == null || !chat.isJsonPrimitive() || !chat.getAsJsonPrimitive().isString()) {
          return Optional.empty();
        }
        return Optional.of(chat.getAsString().trim()).filter(value -> !value.isBlank());
      } catch (RuntimeException ex) {
        return Optional.empty();
      }
    }
    return Optional.of(text);
  }

  private List<MaidAction> parseActions(JsonElement actionsElement) {
    if (actionsElement == null || !actionsElement.isJsonArray()) {
      return List.of();
    }

    List<MaidAction> actions = new ArrayList<>();
    JsonArray actionsArray = actionsElement.getAsJsonArray();
    for (JsonElement actionElement : actionsArray) {
      if (!actionElement.isJsonObject()) {
        throw new IllegalArgumentException("Action must be an object.");
      }
      JsonObject actionObject = actionElement.getAsJsonObject();
      String rawType = stringOrBlank(actionObject, "type");
      Optional<MaidActionType> actionType = MaidActionType.fromInput(rawType);
      if (actionType.isEmpty()) {
        throw new IllegalArgumentException("Unknown action type: " + rawType);
      }
      actions.add(
          new MaidAction(
              actionType.get(),
              stringOrBlank(actionObject, "name"),
              stringOrBlank(actionObject, "target")));
    }
    return List.copyOf(actions);
  }

  private String extractJsonObject(String raw) {
    if (raw == null || raw.isBlank()) {
      return "";
    }
    String trimmed = raw.trim();
    if (trimmed.startsWith("```")) {
      trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
    }

    int start = trimmed.indexOf('{');
    int end = trimmed.lastIndexOf('}');
    if (start < 0 || end <= start) {
      return "";
    }
    return trimmed.substring(start, end + 1);
  }

  private String stringOrBlank(JsonObject object, String key) {
    if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
      return "";
    }
    return object.get(key).getAsString().trim();
  }
}
