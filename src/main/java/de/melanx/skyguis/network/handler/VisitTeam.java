package de.melanx.skyguis.network.handler;

import de.melanx.skyblockbuilder.config.common.PermissionsConfig;
import de.melanx.skyblockbuilder.data.SkyblockSavedData;
import de.melanx.skyblockbuilder.data.Team;
import de.melanx.skyblockbuilder.util.RandomUtility;
import de.melanx.skyblockbuilder.util.WorldUtil;
import de.melanx.skyguis.SkyGUIs;
import de.melanx.skyguis.network.EasyNetwork;
import de.melanx.skyguis.util.LoadingResult;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.moddingx.libx.network.PacketHandler;
import org.moddingx.libx.network.PacketSerializer;

import java.util.UUID;
import java.util.function.Supplier;

public record VisitTeam(UUID team) {

    public static class Handler implements PacketHandler<VisitTeam> {

        @Override
        public Target target() {
            return Target.MAIN_THREAD;
        }

        @Override
        public boolean handle(VisitTeam msg, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return true;
            }

            EasyNetwork network = SkyGUIs.getNetwork();
            if (!PermissionsConfig.Teleports.allowVisits && !player.hasPermissions(1)) {
                network.handleLoadingResult(ctx.get(), LoadingResult.Status.FAIL, Component.translatable("skyblockbuilder.command.disabled.team_visit"));
                return true;
            }

            ServerLevel level = (ServerLevel) player.level();
            SkyblockSavedData data = SkyblockSavedData.get(level);
            Team team = data.getTeam(msg.team);
            if (team == null) {
                // should never be the case
                return true;
            }

            if (!player.hasPermissions(2) && !data.getOrCreateMetaInfo(player).canVisit(level.getGameTime())) {
                network.handleLoadingResult(ctx.get(), LoadingResult.Status.FAIL, Component.translatable("skyblockbuilder.command.error.cooldown",
                        RandomUtility.formattedCooldown(PermissionsConfig.Teleports.visitCooldown - (level.getGameTime() - data.getOrCreateMetaInfo(player).getLastVisitTeleport()))));
                return true;
            }

            if (!team.allowsVisits() && !player.hasPermissions(1)) {
                network.handleLoadingResult(ctx.get(), LoadingResult.Status.FAIL, Component.translatable("skyblockbuilder.command.disabled.visit_team"));
                return true;
            }

            if (team.equals(data.getTeamFromPlayer(player))) {
                network.handleLoadingResult(ctx.get(), LoadingResult.Status.FAIL, Component.translatable("skyblockbuilder.command.error.visit_own_team"));
                return true;
            }

            data.getOrCreateMetaInfo(player).setLastVisitTeleport(level.getGameTime());
            WorldUtil.teleportToIsland(player, team);
            network.handleLoadingResult(ctx.get(), LoadingResult.Status.SUCCESS, Component.translatable("skyblockbuilder.command.success.visit_team", team.getName()));
            return true;
        }
    }

    public static class Serializer implements PacketSerializer<VisitTeam> {

        @Override
        public Class<VisitTeam> messageClass() {
            return VisitTeam.class;
        }

        @Override
        public void encode(VisitTeam msg, FriendlyByteBuf buffer) {
            buffer.writeUUID(msg.team);
        }

        @Override
        public VisitTeam decode(FriendlyByteBuf buffer) {
            return new VisitTeam(buffer.readUUID());
        }
    }
}
