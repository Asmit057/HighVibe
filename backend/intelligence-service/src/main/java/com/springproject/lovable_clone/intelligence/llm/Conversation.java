package com.springproject.lovable_clone.intelligence.llm;

import com.springproject.lovable_clone.intelligence.llm.CodeGenerator.HistoryTurn;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns chat history plus the new request into a strictly alternating user / model conversation
 * that starts with the user, as LLM APIs require. Consecutive turns of the same side are merged.
 */
public final class Conversation {

    private Conversation() {}

    public static List<HistoryTurn> alternating(List<HistoryTurn> history, String currentUserTurn) {
        List<HistoryTurn> turns = new ArrayList<>();
        for (HistoryTurn turn : history) {
            if (turns.isEmpty() && !turn.fromUser()) {
                continue; // a conversation must start with a user turn
            }
            appendOrMerge(turns, turn);
        }
        appendOrMerge(turns, new HistoryTurn(true, currentUserTurn));
        return turns;
    }

    private static void appendOrMerge(List<HistoryTurn> turns, HistoryTurn turn) {
        if (!turns.isEmpty() && turns.getLast().fromUser() == turn.fromUser()) {
            HistoryTurn last = turns.removeLast();
            turns.add(new HistoryTurn(last.fromUser(), last.text() + "\n\n" + turn.text()));
        } else {
            turns.add(turn);
        }
    }
}
