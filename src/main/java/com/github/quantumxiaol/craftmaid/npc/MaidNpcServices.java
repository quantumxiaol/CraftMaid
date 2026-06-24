package com.github.quantumxiaol.craftmaid.npc;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import java.lang.reflect.InvocationTargetException;

public final class MaidNpcServices {
  private static final String CITIZENS_PLUGIN = "Citizens";
  private static final String CITIZENS_SERVICE_CLASS =
      "com.github.quantumxiaol.craftmaid.npc.CitizensMaidNpcService";

  private MaidNpcServices() {}

  public static MaidNpcService create(CraftMaid plugin) {
    if (!plugin.getServer().getPluginManager().isPluginEnabled(CITIZENS_PLUGIN)) {
      return new NoopMaidNpcService();
    }

    try {
      Class<?> serviceClass = Class.forName(CITIZENS_SERVICE_CLASS);
      return (MaidNpcService) serviceClass.getConstructor(CraftMaid.class).newInstance(plugin);
    } catch (ClassNotFoundException
        | NoSuchMethodException
        | InstantiationException
        | IllegalAccessException
        | InvocationTargetException
        | LinkageError ex) {
      plugin.getLogger().warning("Citizens 已启用，但 NPC 服务初始化失败: " + rootMessage(ex));
      return new NoopMaidNpcService();
    }
  }

  private static String rootMessage(Throwable throwable) {
    Throwable cursor = throwable;
    while (cursor.getCause() != null) {
      cursor = cursor.getCause();
    }
    return cursor.getMessage() == null ? cursor.getClass().getSimpleName() : cursor.getMessage();
  }
}
