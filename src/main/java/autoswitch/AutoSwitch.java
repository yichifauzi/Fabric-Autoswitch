package autoswitch;

import autoswitch.config.AutoSwitchConfig;
import autoswitch.config.AutoSwitchMaterialConfig;
import autoswitch.config.AutoSwitchUsableConfig;
import autoswitch.config.io.ConfigEstablishment;
import autoswitch.config.populator.ApiMapGenerator;
import autoswitch.config.populator.AutoSwitchMapsGenerator;
import autoswitch.events.Scheduler;
import autoswitch.events.SwitchEvent;
import autoswitch.util.ApiGenUtil;
import autoswitch.util.EventUtil;
import autoswitch.util.SwitchDataStorage;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.TranslatableText;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

public class AutoSwitch implements ClientModInitializer {

    public static final Logger logger = LogManager.getLogger("AutoSwitch");

    //Create object to store player switch state
    public static final SwitchDataStorage data = new SwitchDataStorage();
    public static final Scheduler scheduler = new Scheduler();

    //Init config
    public static AutoSwitchConfig cfg;
    public static AutoSwitchMaterialConfig matCfg;
    public static AutoSwitchUsableConfig usableCfg;

    public static boolean mowing = true;
    public static int tickTime = 0;

    //Keybindings
    private static KeyBinding autoswitchToggleKeybinding;
    private static KeyBinding mowingWhenFightingToggleKeybinding;

    private boolean doAS = true;
    private boolean onMP = true;

    @Override
    @Environment(EnvType.CLIENT)
    public void onInitializeClient() {
        ApiMapGenerator.createApiMaps();

        ApiGenUtil.pullHookedMods();

        // Create config files and load them
        new ConfigEstablishment();

        // Pull value for delayed switching
        doAS = !cfg.disableSwitchingOnStartup();

        //Populate data on startup
        new AutoSwitchMapsGenerator();

        //Keybindings
        autoswitchToggleKeybinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autoswitch.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "AutoSwitch"
        ));

        mowingWhenFightingToggleKeybinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autoswitch.toggle_mowing",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "AutoSwitch"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(e -> {
            tickTime += 1;

            //Keybindings implementation BEGIN ---
            if (autoswitchToggleKeybinding.wasPressed()) {
                //The toggle
                doAS = !doAS;

                if (cfg.displayToggleMsg()) {
                    //Toggle message
                    TranslatableText msg = new TranslatableText(doAS && (!onMP || cfg.switchInMP()) ?
                            "msg.autoswitch.toggle_true" : "msg.autoswitch.toggle_false");
                    assert e.player != null : "Player was unexpectedly null";
                    //Display msg above hotbar, set false to display in text chat
                    e.player.sendMessage(msg, cfg.toggleMsgOverHotbar());
                }

            }

            if (mowingWhenFightingToggleKeybinding.wasPressed()) {
                mowing = !mowing;

                if (cfg.displayToggleMsg()) {
                    //Toggle message
                    TranslatableText msg = new TranslatableText(mowing || !cfg.controlMowingWhenFighting() ?
                            "msg.autoswitch.mow_true" : "msg.autoswitch.mow_false");
                    assert e.player != null : "Player was unexpectedly null";
                    //Display msg above hotbar, set false to display in text chat
                    e.player.sendMessage(msg, cfg.toggleMsgOverHotbar());
                }
            }
            //Keybindings implementation END ---

            // Tick event system and check if scheduling a switchback is needed via EventUtil
            if (e.player != null) {
                assert e.world != null : "World was null when a player wasn't?!";

                // Schedule switchback iff it is needed
                EventUtil.eventHandler(e.world, tickTime, 0, SwitchEvent.SWITCHBACK.setPlayer(e.player));

                // Tick event system clock
                scheduler.execute(tickTime);
            }

        });

        //Check if the client is on a multiplayer server
        //This is only called when starting a SP world, not on server join
        ServerLifecycleEvents.SERVER_STARTED.register((minecraftServer -> {
            onMP = false;
            doAS = !cfg.disableSwitchingOnStartup();
        }));

        //Disable onMP when leaving SP
        ServerLifecycleEvents.SERVER_STOPPED.register((minecraftServer -> {
            onMP = true;
            doAS = !cfg.disableSwitchingOnStartup();
        }));

        //Block Swaps
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) ->
                EventUtil.schedulePrimaryEvent(world, SwitchEvent.ATTACK.setPlayer(player).setOnMP(onMP)
                        .setDoSwitch(doAS).setDoSwitchType(cfg.switchForBlocks())
                        .setProtoTarget(world.getBlockState(pos))));

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) ->
                EventUtil.schedulePrimaryEvent(world, SwitchEvent.USE.setPlayer(player).setOnMP(onMP)
                        .setDoSwitch(doAS).setDoSwitchType(cfg.switchUseActions())
                        .setProtoTarget(world.getBlockState(hitResult.getBlockPos()))));

        //Entity Swaps
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
                EventUtil.schedulePrimaryEvent(world, SwitchEvent.ATTACK.setPlayer(player).setOnMP(onMP)
                        .setDoSwitch(doAS).setDoSwitchType(cfg.switchForMobs())
                        .setProtoTarget(entity)));

        UseEntityCallback.EVENT.register((player, world, hand, entity, entityHitResult) ->
                EventUtil.schedulePrimaryEvent(world, SwitchEvent.USE.setPlayer(player).setOnMP(onMP)
                        .setDoSwitch(doAS).setDoSwitchType(cfg.switchUseActions())
                        .setProtoTarget(entity)));

        //Notify when AS Loaded
        logger.info("AutoSwitch Loaded");

    }
}

