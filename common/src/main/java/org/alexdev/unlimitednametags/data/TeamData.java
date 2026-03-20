package org.alexdev.unlimitednametags.data;

import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import com.google.common.collect.Sets;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

@Getter
@Setter
public class TeamData {

    private final String teamName;
    private WrapperPlayServerTeams.ScoreBoardTeamInfo teamInfo;
    private final Set<String> members;
    private boolean changedVisibility = false;

    public TeamData(@NotNull String teamName, @NotNull WrapperPlayServerTeams.ScoreBoardTeamInfo teamInfo) {
        this.teamName = teamName;
        this.teamInfo = teamInfo;
        this.members = Sets.newConcurrentHashSet();
    }

    public TeamData(@NotNull String teamName, @NotNull WrapperPlayServerTeams.ScoreBoardTeamInfo teamInfo, @NotNull Set<String> members) {
        this.teamName = teamName;
        this.teamInfo = teamInfo;
        this.members = Sets.newConcurrentHashSet(members);
    }

}
