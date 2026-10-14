package com.apexgrid.engine.config;

import com.apexgrid.engine.dto.request.SubmitScoreRequestDTO;
import com.apexgrid.engine.dto.response.MatchResponseDTO;
import com.apexgrid.engine.entity.Player;
import com.apexgrid.engine.entity.Team;
import com.apexgrid.engine.entity.Tournament;
import com.apexgrid.engine.enums.GameType;
import com.apexgrid.engine.enums.TournamentStatus;
import com.apexgrid.engine.repository.TeamRepository;
import com.apexgrid.engine.repository.TournamentRepository;
import com.apexgrid.engine.service.BracketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Seeds the demo tournaments on first boot.
 *
 * Four single-elimination brackets are created (Valorant, CS2, Fortnite and
 * Rocket League). The opening round of each bracket is pre-completed so the
 * standings leaderboard and the automatic winner promotion are immediately
 * visible in the SPA.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final TournamentRepository tournamentRepository;
    private final TeamRepository teamRepository;
    private final BracketService bracketService;

    private record TeamSeed(String name, String role) {
    }

    private record Result(int scoreA, int scoreB) {
    }

    @Override
    public void run(String... args) {
        long existing = tournamentRepository.count();
        if (existing > 0) {
            log.info("Database already contains {} tournament(s). Skipping seeder.", existing);
            return;
        }

        log.info("Seeding ApexGrid demo tournaments...");

        seed("Apex Masters 2026", GameType.VALORANT,
                new TeamSeed[]{
                        new TeamSeed("Sentinels", "Duelist"),
                        new TeamSeed("Fnatic", "Initiator"),
                        new TeamSeed("Paper Rex", "Duelist"),
                        new TeamSeed("Team Liquid", "Sentinel")
                },
                new Result(13, 9), new Result(11, 13), null);

        seed("Major Championship 2026", GameType.CS2,
                new TeamSeed[]{
                        new TeamSeed("Natus Vincere", "AWPer"),
                        new TeamSeed("FaZe Clan", "IGL"),
                        new TeamSeed("Vitality", "Rifler"),
                        new TeamSeed("G2 Esports", "Entry")
                },
                new Result(16, 12), new Result(14, 16), null);

        seed("Fortnite Champion Series", GameType.FORTNITE,
                new TeamSeed[]{
                        new TeamSeed("Zone Warriors", "Builder"),
                        new TeamSeed("Storm Walkers", "Fragger"),
                        new TeamSeed("Victory Vipers", "Support"),
                        new TeamSeed("Loot Titans", "Anchor")
                },
                new Result(85, 62), null, null);

        seed("World Invitational", GameType.ROCKET_LEAGUE,
                new TeamSeed[]{
                        new TeamSeed("Karmine Corp", "Striker"),
                        new TeamSeed("Gen.G Mobil1", "Midfield"),
                        new TeamSeed("BDS Esports", "Goalkeeper"),
                        new TeamSeed("G2 Stride", "Flex")
                },
                new Result(4, 2), new Result(3, 1), new Result(4, 3));

        log.info("Demo tournaments seeded successfully.");
    }

    private void seed(String title, GameType game, TeamSeed[] teamSeeds,
                      Result semiOne, Result semiTwo, Result grandFinal) {

        Tournament tournament = tournamentRepository.save(
                Tournament.builder()
                        .title(title)
                        .gameType(game)
                        .maxTeams(teamSeeds.length)
                        .status(TournamentStatus.REGISTRATION_OPEN)
                        .registeredTeams(new ArrayList<>())
                        .build()
        );

        List<Team> created = new ArrayList<>();
        for (TeamSeed seed : teamSeeds) {
            Team team = Team.builder()
                    .teamName(seed.name())
                    .captainEmail(seed.name().toLowerCase().replaceAll("[^a-z0-9]", "") + "@apexgrid.io")
                    .tournament(tournament)
                    .players(new ArrayList<>())
                    .build();

            team.getPlayers().add(Player.builder()
                    .gamerTag(seed.name().replaceAll("\\s+", "") + "_Cap")
                    .inGameRole(seed.role())
                    .team(team)
                    .build());

            created.add(teamRepository.save(team));
        }

        tournament.setRegisteredTeams(created);
        tournamentRepository.save(tournament);

        List<MatchResponseDTO> fixtures = bracketService.generateBracket(tournament.getId());

        List<MatchResponseDTO> openingRound = fixtures.stream()
                .filter(m -> Integer.valueOf(1).equals(m.getRoundNumber()))
                .sorted(Comparator.comparing(MatchResponseDTO::getMatchNumberInRound))
                .toList();

        Result[] results = {semiOne, semiTwo};
        for (int i = 0; i < openingRound.size() && i < results.length; i++) {
            Result result = results[i];
            if (result == null) {
                continue;
            }
            MatchResponseDTO fixture = openingRound.get(i);
            if (fixture.getTeamAName().equals("TBD") || fixture.getTeamBName().equals("TBD")) {
                continue;
            }
            bracketService.submitScore(fixture.getMatchId(), toRequest(result));
        }

        if (grandFinal != null) {
            bracketService.getTournamentMatches(tournament.getId()).stream()
                    .filter(m -> Integer.valueOf(2).equals(m.getRoundNumber()))
                    .findFirst()
                    .filter(m -> !m.getTeamAName().equals("TBD") && !m.getTeamBName().equals("TBD"))
                    .ifPresent(m -> bracketService.submitScore(m.getMatchId(), toRequest(grandFinal)));
        }

        log.info("Seeded tournament '{}' ({} teams).", title, teamSeeds.length);
    }

    private SubmitScoreRequestDTO toRequest(Result result) {
        SubmitScoreRequestDTO dto = new SubmitScoreRequestDTO();
        dto.setScoreTeamA(result.scoreA());
        dto.setScoreTeamB(result.scoreB());
        return dto;
    }
}
