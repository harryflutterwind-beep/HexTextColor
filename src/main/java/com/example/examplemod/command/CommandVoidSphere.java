// src/main/java/com/example/examplemod/command/CommandVoidSphere.java
package com.example.examplemod.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.WorldServer;

import java.util.Random;

/**
 * /voidsphere <radius> [points] [shell|fill]
 *
 * Spawns a "void" particle sphere around the executing player, using the SAME particle palette
 * you already use for your Void/Null Shell effects:
 *   - portal (wisps)
 *   - witchMagic (purple shimmer)
 *   - mobSpell with RGB (deep purple glow)
 *
 * Defaults:
 *   radius = 6
 *   points = auto (scaled by radius)
 *   mode   = shell
 *
 * Safety clamps:
 *   radius: 0.5 .. 64
 *   points: 48 .. 3200
 */
public class CommandVoidSphere extends CommandBase {

    private static final Random RAND = new Random();

    @Override
    public String getCommandName() {
        return "voidsphere";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/voidsphere <radius> [points] [shell|fill]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2; // OP by default (safer). Set to 0 if you want everyone to use it.
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP)) {
            sender.addChatMessage(new ChatComponentText("§cPlayer-only command."));
            return;
        }

        EntityPlayerMP p = (EntityPlayerMP) sender;
        if (!(p.worldObj instanceof WorldServer)) {
            sender.addChatMessage(new ChatComponentText("§cServer world not available."));
            return;
        }

        WorldServer ws = (WorldServer) p.worldObj;

        double radius = 6.0D;
        int points = -1; // auto
        boolean fill = false; // shell by default

        if (args.length >= 1) {
            try { radius = Double.parseDouble(args[0]); } catch (NumberFormatException ignored) {}
        }
        radius = Math.max(0.5D, Math.min(64.0D, radius));

        if (args.length >= 2) {
            try { points = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
        }

        if (args.length >= 3) {
            String mode = args[2];
            if ("fill".equalsIgnoreCase(mode) || "filled".equalsIgnoreCase(mode)) fill = true;
        }

        // Auto points: scales with surface area for shell, "volumetric feel" for fill.
        if (points <= 0) {
            if (fill) {
                points = (int) Math.round(22.0D * radius * radius);
            } else {
                points = (int) Math.round(28.0D * radius * radius);
            }
        }
        points = Math.max(48, Math.min(3200, points));

        double cx = p.posX;
        double cy = p.posY + 0.9D;
        double cz = p.posZ;

        // Deep purple glow (same vibe as your void gravity particles)
        final double GLOW_R = 0.55D;
        final double GLOW_G = 0.06D;
        final double GLOW_B = 0.78D;

        for (int i = 0; i < points; i++) {
            // Random direction on unit sphere
            double u = RAND.nextDouble();
            double v = RAND.nextDouble();
            double theta = 2.0D * Math.PI * u;
            double phi = Math.acos(2.0D * v - 1.0D);

            double sx = Math.sin(phi) * Math.cos(theta);
            double sy = Math.sin(phi) * Math.sin(theta);
            double sz = Math.cos(phi);

            double r = radius;
            if (fill) {
                // Uniform within sphere: r = R * cbrt(t)
                r = radius * Math.cbrt(RAND.nextDouble());
            } else {
                // Slight thickness to make shell easier to see
                r = radius + (RAND.nextDouble() - 0.5D) * 0.28D;
            }

            double x = cx + sx * r;
            double y = cy + sy * r;
            double z = cz + sz * r;

            // portal wisps (exact point)
            ws.func_147487_a("portal", x, y, z, 1, 0, 0, 0, 0.01D);

            // witch shimmer occasionally (exact point)
            if ((i & 3) == 0) {
                ws.func_147487_a("witchMagic", x, y, z, 1, 0, 0, 0, 0.01D);
            }

            // deep purple glow specks (mobSpell uses RGB in dx/dy/dz when count=0)
            if ((i % 2) == 0) {
                ws.func_147487_a("mobSpell", x, y, z, 0, GLOW_R, GLOW_G, GLOW_B, 1.0D);
            }
        }

        sender.addChatMessage(new ChatComponentText(
                "§7[VoidSphere] §fRadius §b" + radius + "§f, points §b" + points + "§f, mode §b" + (fill ? "fill" : "shell")
        ));
    }
}
