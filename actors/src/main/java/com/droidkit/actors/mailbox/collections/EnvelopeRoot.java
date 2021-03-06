package com.droidkit.actors.mailbox.collections;

import com.droidkit.actors.ActorTime;
import com.droidkit.actors.mailbox.Envelope;
import com.droidkit.actors.mailbox.MailboxesQueue;
import com.droidkit.actors.mailbox.collections.sparse.SparseArray;

import java.util.*;

/**
 * Created by ex3ndr on 28.10.14.
 */
public class EnvelopeRoot {

    private static final long MULTIPLE = 10000L;

    private final HashSet<Long> usedSlot = new HashSet<Long>();

    private final HashMap<Integer, EnvelopeCollection> collections = new HashMap<Integer, EnvelopeCollection>();
    private final HashMap<Integer, Long> lastTopKey = new HashMap<Integer, Long>();
    private final TreeMap<Long, EnvelopeCollection> sortedCollection = new TreeMap<Long, EnvelopeCollection>();

    private MailboxesQueue queue;

    public EnvelopeRoot(MailboxesQueue queue) {
        this.queue = queue;
    }

    public synchronized void attachCollection(EnvelopeCollection collection) {
        long key = collection.getTopKey();

        if (!collections.containsKey(collection.getId())) {
            collections.put(collection.getId(), collection);
            lastTopKey.put(collection.getId(), key);

            if (key > 0) {
                sortedCollection.put(key, collection);
            }
        }

        queue.notifyQueueChanged();
    }

    public synchronized void detachCollection(EnvelopeCollection collection) {
        if (!collections.containsKey(collection.getId())) {
            return;
        }

        collections.remove(collection.getId());

        Long prevKey = lastTopKey.get(collection.getId());
        lastTopKey.remove(collection.getId());
        if (prevKey > 0) {
            sortedCollection.remove(prevKey);
        }
    }

    public synchronized FetchResult fetchCollection(long time) {
        Map.Entry<Long, EnvelopeCollection> res = sortedCollection.isEmpty() ? null : sortedCollection.firstEntry();
        if (res != null) {
            EnvelopeCollection collection = res.getValue();
            EnvelopeCollection.FetchResult envelope = collection.fetchEnvelope(time);
            if (envelope != null) {
                if (envelope.getEnvelope() != null) {
                    detachCollection(collection);
                    FetchResult result = FetchResult.envelope(envelope.getEnvelope().getEnvelope());
                    envelope.recycle();
                    return result;
                } else {
                    FetchResult result = FetchResult.delay(envelope.getDelay());
                    envelope.recycle();
                    return result;
                }
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    synchronized void changedTopKey(EnvelopeCollection collection) {
        if (!collections.containsKey(collection.getId())) {
            return;
        }

        long key = collection.getTopKey();

        Long prevKey = lastTopKey.get(collection.getId());
        lastTopKey.remove(collection.getId());
        if (prevKey > 0) {
            sortedCollection.remove(prevKey);
        }

        lastTopKey.put(collection.getId(), key);
        if (key > 0) {
            sortedCollection.put(key, collection);
        }

        queue.notifyQueueChanged();
    }

    synchronized long buildKey(long time) {
        long currentTime = ActorTime.currentTime();
        if (time < currentTime) {
            time = currentTime;
        }

        // Clean Up old slots
        Iterator<Long> iterator = usedSlot.iterator();
        while (iterator.hasNext()) {
            long t = iterator.next();
            if (t < currentTime * MULTIPLE) {
                iterator.remove();
            }
        }

        long shift = 0;
        while (usedSlot.contains(time * MULTIPLE + shift)) {
            shift++;
        }

        usedSlot.add(time * MULTIPLE + shift);
        return time * MULTIPLE + shift;
    }

    public static class FetchResult {

        private static ThreadLocal<FetchResult> RESULT_CACHE = new ThreadLocal<FetchResult>();

        public static FetchResult envelope(Envelope envelope) {
            FetchResult res = RESULT_CACHE.get();
            if (res != null) {
                RESULT_CACHE.remove();
                res.update(envelope, 0);
            } else {
                res = new FetchResult(envelope);
            }

            return res;
        }

        public static FetchResult delay(long delay) {
            FetchResult res = RESULT_CACHE.get();
            if (res != null) {
                RESULT_CACHE.remove();
                res.update(null, delay);
            } else {
                res = new FetchResult(delay);
            }

            return res;
        }

        private Envelope envelope;
        private long delay;

        private FetchResult(Envelope envelope) {
            this.envelope = envelope;
        }

        private FetchResult(long delay) {
            this.delay = delay;
        }

        public Envelope getEnvelope() {
            return envelope;
        }

        public long getDelay() {
            return delay;
        }

        public void update(Envelope envelope, long delay) {
            this.envelope = envelope;
            this.delay = delay;
        }

        public void recycle() {
            RESULT_CACHE.set(this);
        }
    }
}
