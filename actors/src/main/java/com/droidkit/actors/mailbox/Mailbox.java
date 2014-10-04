package com.droidkit.actors.mailbox;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Actor mailbox, queue of envelopes.
 *
 * @author Stepan Ex3NDR Korshakov (me@ex3ndr.com)
 */
public class Mailbox {
    private final Map<Long, Envelope> envelopes = Collections.synchronizedMap(new HashMap<Long, Envelope>());

    private MailboxesQueue queue;

    /**
     * Creating mailbox
     *
     * @param queue MailboxesQueue
     */
    public Mailbox(MailboxesQueue queue) {
        this.queue = queue;
    }

    /**
     * Send envelope at time
     *
     * @param envelope envelope
     * @param time     time
     */
    public void schedule(Envelope envelope, long time) {
        if (envelope.getMailbox() != this) {
            throw new RuntimeException("envelope.mailbox != this mailbox");
        }

        long id = queue.sendEnvelope(envelope, time);

        synchronized (envelopes) {
            envelopes.put(id, envelope);
        }
    }

    /**
     * Send envelope once at time
     *
     * @param envelope envelope
     * @param time     time
     */
    public void scheduleOnce(Envelope envelope, long time) {
        if (envelope.getMailbox() != this) {
            throw new RuntimeException("envelope.mailbox != this mailbox");
        }

        synchronized (envelopes) {
            Iterator<Map.Entry<Long, Envelope>> iterator = envelopes.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Long, Envelope> entry = iterator.next();
                if (isEqualEnvelope(entry.getValue(), envelope)) {
                    queue.removeEnvelope(entry.getKey());
                    iterator.remove();
                }
            }
        }

        schedule(envelope, time);
    }

    void removeEnvelope(long key) {
        synchronized (envelopes) {
            envelopes.remove(key);
        }
    }

    /**
     * Override this if you need to change filtering for scheduleOnce behaviour.
     * By default it check equality only of class names.
     *
     * @param a
     * @param b
     * @return is equal
     */
    protected boolean isEqualEnvelope(Envelope a, Envelope b) {
        return a.getMessage().getClass() == b.getMessage().getClass();
    }

    public Envelope[] allEnvelopes() {
        synchronized (envelopes) {
            return envelopes.values().toArray(new Envelope[0]);
        }
    }

    public synchronized int getMailboxSize() {
        return envelopes.size();
    }

    public synchronized void clear() {
        synchronized (envelopes) {
            for (Map.Entry<Long, Envelope> entry : envelopes.entrySet()) {
                queue.removeEnvelope(entry.getKey());
            }
            envelopes.clear();
        }
    }
}