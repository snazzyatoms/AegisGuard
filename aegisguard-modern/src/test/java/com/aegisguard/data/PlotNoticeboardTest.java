package com.aegisguard.data;

import com.aegisguard.profile.PlotNotice;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 4 (Realm Profiles and Noticeboards) - plugin-independent unit tests for the
 * owner-moderated noticeboard stored directly on {@link Plot}.
 */
class PlotNoticeboardTest {

    @Test
    void newPlotsHaveAnEmptyNoticeboard() {
        Plot plot = new Plot(UUID.randomUUID(), UUID.randomUUID(), "OwnerName", "world", 0, 0, 20, 20);
        assertTrue(plot.getNoticeboard().isEmpty());
    }

    @Test
    void postingANoticeAddsItToTheEndOfTheBoard() {
        Plot plot = new Plot(UUID.randomUUID(), UUID.randomUUID(), "OwnerName", "world", 0, 0, 20, 20);
        UUID author = UUID.randomUUID();

        PlotNotice notice = PlotNotice.post(author, "Owner", "Rules: no griefing.");
        plot.postNotice(notice, 8);

        List<PlotNotice> board = plot.getNoticeboard();
        assertEquals(1, board.size());
        assertEquals("Rules: no griefing.", board.get(0).getText());
        assertEquals(author, board.get(0).getAuthorId());
    }

    @Test
    void postingBeyondMaxEntriesDropsTheOldestNoticeFirst() {
        Plot plot = new Plot(UUID.randomUUID(), UUID.randomUUID(), "OwnerName", "world", 0, 0, 20, 20);
        UUID author = UUID.randomUUID();

        PlotNotice first = PlotNotice.post(author, "Owner", "First");
        PlotNotice second = PlotNotice.post(author, "Owner", "Second");
        PlotNotice third = PlotNotice.post(author, "Owner", "Third");

        plot.postNotice(first, 2);
        plot.postNotice(second, 2);
        plot.postNotice(third, 2);

        List<PlotNotice> board = plot.getNoticeboard();
        assertEquals(2, board.size(), "Oldest notice must be evicted once the cap is reached");
        assertEquals("Second", board.get(0).getText());
        assertEquals("Third", board.get(1).getText());
        assertNull(plot.getNotice(first.getId()));
    }

    @Test
    void removeNoticeDeletesOnlyTheTargetedEntry() {
        Plot plot = new Plot(UUID.randomUUID(), UUID.randomUUID(), "OwnerName", "world", 0, 0, 20, 20);
        UUID author = UUID.randomUUID();

        PlotNotice keep = PlotNotice.post(author, "Owner", "Keep me");
        PlotNotice remove = PlotNotice.post(author, "Owner", "Remove me");
        plot.postNotice(keep, 8);
        plot.postNotice(remove, 8);

        assertTrue(plot.removeNotice(remove.getId()));
        assertFalse(plot.removeNotice(remove.getId()), "Removing twice must be a safe no-op");
        assertEquals(1, plot.getNoticeboard().size());
        assertEquals("Keep me", plot.getNoticeboard().get(0).getText());
    }

    @Test
    void clearNoticeboardRemovesEveryEntry() {
        Plot plot = new Plot(UUID.randomUUID(), UUID.randomUUID(), "OwnerName", "world", 0, 0, 20, 20);
        UUID author = UUID.randomUUID();
        plot.postNotice(PlotNotice.post(author, "Owner", "One"), 8);
        plot.postNotice(PlotNotice.post(author, "Owner", "Two"), 8);

        plot.clearNoticeboard();

        assertTrue(plot.getNoticeboard().isEmpty());
    }

    @Test
    void serializingAndDeserializingNoticeboardRoundTripsExactly() {
        UUID owner = UUID.randomUUID();
        UUID author = UUID.randomUUID();
        Plot source = new Plot(UUID.randomUUID(), owner, "OwnerName", "world", 0, 0, 20, 20);
        // Content deliberately includes delimiter characters ('|', '~', ';') and a newline to
        // prove the Base64 encoding protects the serialization format.
        source.postNotice(PlotNotice.post(author, "Owner", "Shop open 9-5 | ring bell; back soon~\nCome in!"), 8);
        source.postNotice(PlotNotice.post(author, "Owner", "Second notice"), 8);

        String blob = source.serializeNoticeboard();
        assertFalse(blob.isEmpty());

        Plot target = new Plot(UUID.randomUUID(), owner, "OwnerName", "world", 0, 0, 20, 20);
        target.deserializeNoticeboard(blob);

        List<PlotNotice> roundTripped = target.getNoticeboard();
        assertEquals(2, roundTripped.size());
        assertEquals("Shop open 9-5 | ring bell; back soon~\nCome in!", roundTripped.get(0).getText());
        assertEquals("Second notice", roundTripped.get(1).getText());
        assertEquals("Owner", roundTripped.get(0).getAuthorName());
        assertEquals(author, roundTripped.get(0).getAuthorId());
    }

    @Test
    void emptyNoticeboardSerializesToAnEmptyStringAndRoundTripsCleanly() {
        Plot plot = new Plot(UUID.randomUUID(), UUID.randomUUID(), "OwnerName", "world", 0, 0, 20, 20);
        assertEquals("", plot.serializeNoticeboard());

        plot.deserializeNoticeboard("");
        plot.deserializeNoticeboard(null);
        assertTrue(plot.getNoticeboard().isEmpty());
    }
}
