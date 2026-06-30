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
      String chat = stringOrBlank(root, "chat");
      List<MaidAction> actions = parseActions(root.get("actions"));
      return Optional.of(new MaidActionPlan(chat, actions));
    } catch (RuntimeException ex) {
      return Optional.empty();
    }
  }

  private List<MaidAction> parseActions(JsonElement actionsElement) {
    if (actionsElement == null || !actionsElement.isJsonArray()) {
      return List.of();
    }

    List<MaidAction> actions = new ArrayList<>();
    JsonArray actionsArray = actionsElement.getAsJsonArray();
    for (JsonElement actionElement : actionsArray) {
      if (!actionElement.isJsonObject()) {
        continue;
      }
      JsonObject actionObject = actionElement.getAsJsonObject();
      Optional<MaidActionType> actionType =
          MaidActionType.fromInput(stringOrBlank(actionObject, "type"));
      if (actionType.isEmpty()) {
        continue;
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
