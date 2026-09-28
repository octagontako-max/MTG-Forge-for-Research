package forge.game;

import forge.game.card.CardView;
import forge.game.event.GameEventCardChangeZone;
import forge.util.ResearchMode;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Research-only JSONL output.
 *
 * <p>Existing GameLog entries are mirrored without changing normal logging.
 * Research-only structured observations can also be appended through dedicated
 * methods. All output is disabled when research mode is off.</p>
 */
final class ResearchGameLogJson {
    static final String SYSTEM_PROPERTY = "forge.research.jsonLog";
    static final String ENVIRONMENT_VARIABLE = "FORGE_RESEARCH_JSON_LOG";
    private static final String DEFAULT_PATH = "research-playlog.jsonl";

    private static final AtomicLong NEXT_LOG_ID = new AtomicLong(1);
    private static boolean initialized = false;
    private static boolean warned = false;

    private ResearchGameLogJson() {
    }

    static long nextLogId() {
        return NEXT_LOG_ID.getAndIncrement();
    }

    static synchronized void append(long logId, long eventIndex, GameLogEntry entry) {
        if (!ResearchMode.isEnabled()) {
            return;
        }

        CardView source = entry.sourceCard();
        StringBuilder sb = beginRecord(logId, eventIndex, "GAME_LOG");
        field(sb, "type", entry.type().name()).append(',');
        field(sb, "caption", entry.type().getCaption()).append(',');
        field(sb, "message", entry.message()).append(',');
        if (source == null) {
            sb.append("\"source_card\":null");
        } else {
            field(sb, "source_card", source.getName());
        }
        sb.append('}');
        writeLine(sb.toString());
    }

    static synchronized void appendHandAdd(long logId, long eventIndex,
                                           GameEventCardChangeZone event,
                                           boolean draw) {
        if (!ResearchMode.isEnabled() || event == null || event.to() == null || event.card() == null) {
            return;
        }

        StringBuilder sb = beginRecord(logId, eventIndex, "HAND_DELTA");
        field(sb, "action", draw ? "DRAW" : "HAND_ADD").append(',');
        field(sb, "player", event.to().player() == null ? null : event.to().player().getName()).append(',');
        field(sb, "card", event.card().getName()).append(',');
        field(sb, "from_zone", event.from() == null ? null : event.from().zoneType().name()).append(',');
        field(sb, "to_zone", event.to().zoneType().name());
        sb.append('}');
        writeLine(sb.toString());
    }

    static synchronized void appendUnflaggedDiscard(long logId, long eventIndex,
                                                    GameEventCardChangeZone event) {
        if (!ResearchMode.isEnabled() || event == null || event.from() == null
                || event.to() == null || event.card() == null) {
            return;
        }

        StringBuilder sb = beginRecord(logId, eventIndex, "HAND_DELTA_EXCEPTION");
        field(sb, "action", "UNFLAGGED_HAND_TO_GRAVEYARD").append(',');
        field(sb, "classification", "DISCARD_CANDIDATE").append(',');
        field(sb, "player", event.from().player() == null ? null : event.from().player().getName()).append(',');
        field(sb, "card", event.card().getName()).append(',');
        field(sb, "from_zone", event.from().zoneType().name()).append(',');
        field(sb, "to_zone", event.to().zoneType().name()).append(',');
        field(sb, "reason", "outside_Player.discard");
        sb.append('}');
        writeLine(sb.toString());
    }

    static synchronized void appendDecision(long logId, long eventIndex, ResearchDecisionRecord decision) {
        if (!ResearchMode.isEnabled() || decision == null) {
            return;
        }

        StringBuilder sb = beginRecord(logId, eventIndex, "AI_DECISION");
        numberField(sb, "decision_id", decision.decisionId()).append(',');
        field(sb, "decision_type", decision.decisionType()).append(',');
        field(sb, "context", decision.context()).append(',');
        numberField(sb, "turn", decision.turn()).append(',');
        field(sb, "phase", decision.phase()).append(',');
        field(sb, "player", decision.player()).append(',');
        numberField(sb, "stack_size", decision.stackSize()).append(',');
        numberField(sb, "input_candidate_count", decision.inputCandidateCount()).append(',');
        numberField(sb, "expanded_candidate_count", decision.expandedCandidateCount()).append(',');
        numberField(sb, "evaluated_count", decision.evaluatedCandidates().size()).append(',');
        field(sb, "outcome", decision.outcome()).append(',');
        sb.append("\"chosen\":");
        appendCandidate(sb, decision.chosen());
        sb.append(',');
        sb.append("\"evaluated_candidates\":[");
        for (int i = 0; i < decision.evaluatedCandidates().size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            appendCandidate(sb, decision.evaluatedCandidates().get(i));
        }
        sb.append(']');
        sb.append('}');
        writeLine(sb.toString());
    }

    private static void appendCandidate(StringBuilder sb, ResearchDecisionRecord.Candidate candidate) {
        if (candidate == null) {
            sb.append("null");
            return;
        }
        sb.append('{');
        numberField(sb, "evaluation_index", candidate.evaluationIndex()).append(',');
        field(sb, "card", candidate.card()).append(',');
        sb.append("\"card_id\":");
        if (candidate.cardId() == null) {
            sb.append("null");
        } else {
            sb.append(candidate.cardId());
        }
        sb.append(',');
        field(sb, "origin_zone", candidate.originZone()).append(',');
        field(sb, "api", candidate.api()).append(',');
        field(sb, "ability", candidate.ability()).append(',');
        field(sb, "result", candidate.result());
        sb.append('}');
    }

    private static StringBuilder beginRecord(long logId, long eventIndex, String recordType) {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        field(sb, "schema", "forge-research-gamelog-v1").append(',');
        numberField(sb, "log_id", logId).append(',');
        numberField(sb, "event_index", eventIndex).append(',');
        numberField(sb, "timestamp_ms", System.currentTimeMillis()).append(',');
        field(sb, "record_type", recordType).append(',');
        return sb;
    }

    private static void writeLine(String json) {
        Path path = outputPath();
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            StandardOpenOption[] options;
            if (!initialized) {
                options = new StandardOpenOption[] {
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE
                };
                initialized = true;
            } else {
                options = new StandardOpenOption[] {
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND,
                        StandardOpenOption.WRITE
                };
            }

            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, options)) {
                writer.write(json);
                writer.newLine();
            }
        } catch (IOException | RuntimeException ex) {
            // Research logging must never change normal Forge execution.
            if (!warned) {
                warned = true;
                System.err.println("Research JSON log write failed: " + ex.getMessage());
            }
        }
    }

    private static Path outputPath() {
        String configured = System.getProperty(SYSTEM_PROPERTY);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(ENVIRONMENT_VARIABLE);
        }
        if (configured == null || configured.isBlank()) {
            configured = DEFAULT_PATH;
        }
        return Path.of(configured);
    }

    private static StringBuilder field(StringBuilder sb, String key, String value) {
        sb.append('"').append(escape(key)).append("\":");
        if (value == null) {
            sb.append("null");
        } else {
            sb.append('"').append(escape(value)).append('"');
        }
        return sb;
    }

    private static StringBuilder numberField(StringBuilder sb, String key, long value) {
        return sb.append('"').append(escape(key)).append("\":").append(value);
    }

    private static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int)c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
