package org.kylepitcock.legacyconnector.v1_20_1.migrate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WorldApi1201Test {
    @BeforeAll
    static void bootstrap() throws ReflectiveOperationException {
        var dist = FMLLoader.class.getDeclaredField("dist");
        dist.setAccessible(true);
        dist.set(null, Dist.CLIENT);
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void scoreboardResetsOnlyTheRequestedScores() {
        var scoreboard = new Scoreboard();
        var first = scoreboard.addObjective("first", ObjectiveCriteria.DUMMY, Component.literal("First"),
                ObjectiveCriteria.RenderType.INTEGER, false, null);
        var second = scoreboard.addObjective("second", ObjectiveCriteria.DUMMY, Component.literal("Second"),
                ObjectiveCriteria.RenderType.INTEGER, false, null);
        var player = ScoreHolder.forNameOnly("player");
        var other = ScoreHolder.forNameOnly("other");
        scoreboard.getOrCreatePlayerScore(player, first).set(1);
        scoreboard.getOrCreatePlayerScore(player, second).set(2);
        scoreboard.getOrCreatePlayerScore(other, first).set(3);

        MiddleCompat.resetPlayerScore(scoreboard, "player", first);
        assertEquals(2, scoreboard.listPlayerScores(player).getInt(second));
        assertEquals(1, scoreboard.listPlayerScores(player).size());
        MiddleCompat.resetPlayerScore(scoreboard, "player", null);
        assertEquals(3, scoreboard.listPlayerScores(other).getInt(first));
        assertEquals(0, scoreboard.listPlayerScores(player).size());
    }

    @Test
    void particleReaderRespectsTheLookupAndLeavesFollowingInputUntouched() throws CommandSyntaxException {
        var lookup = BuiltInRegistries.PARTICLE_TYPE.asLookup().filterElements(particle -> particle == ParticleTypes.SMOKE);
        var reader = new StringReader("smoke 2");
        assertSame(ParticleTypes.SMOKE, MiddleCompat.readParticle(reader, lookup));
        assertEquals(" 2", reader.getRemaining());
        assertThrows(CommandSyntaxException.class, () -> MiddleCompat.readParticle(new StringReader("minecraft:flame"), lookup));
    }
}
