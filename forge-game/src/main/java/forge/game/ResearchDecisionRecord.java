package forge.game;

import java.util.List;

/**
 * Immutable research-only snapshot of one AI decision.
 *
 * <p>Kept in forge-game so AI modules can report decisions without making
 * the game logger depend on forge-ai implementation classes.</p>
 */
public record ResearchDecisionRecord(
        long decisionId,
        String decisionType,
        String context,
        int turn,
        String phase,
        String player,
        int stackSize,
        int inputCandidateCount,
        int expandedCandidateCount,
        List<Candidate> evaluatedCandidates,
        String outcome,
        Candidate chosen
) {
    public record Candidate(
            int evaluationIndex,
            String card,
            Integer cardId,
            String originZone,
            String api,
            String ability,
            String result
    ) {
    }
}
