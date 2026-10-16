package dev.woflo.vixen;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * A tail snapshot (work item V2, {@code vixen-tail-c2/1}): a timeline exported from a checkpoint {@code from}. It holds the root
 * configuration, the seed of the leaf the checkpoint stands on, the forks whose handoffs are still open at {@code from}, the influences not
 * yet released, and a SHA-256 {@code witness} of the canonical JSON of everything folded. {@link #restore} builds a timeline that samples
 * bitwise as the full lineage for every {@code x >= from}; the folded history stays provable by recomputing the witness from the entries
 * ({@link #chain}).
 *
 * @param folded how many forks and influences are behind the checkpoint, for the record
 */
public record Tail(Config config, Seed leafSeed, double from, long folded, String witness, List<Timeline.Entry> open, List<Influence> influences,boolean coherent) {
    public Tail(Config config,Seed leafSeed,double from,long folded,String witness,List<Timeline.Entry> open,List<Influence> influences){this(config,leafSeed,from,folded,witness,open,influences,false);}
    public static final String COHERENT_ALGORITHM="vixen-tail-c2/3";
    public static final String ALGORITHM = "vixen-tail-c2/1";

    public Tail {
        open = List.copyOf(open);
        influences = List.copyOf(influences);
    }

    /** The witness of {@code folded} entries and influences after {@code previous} (empty for the first fold). */
    public static String chain(String previous, List<Timeline.Entry> entries, List<Influence> influences) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"entries\":[");
        for (int i = 0; i < entries.size(); i++) {
            Timeline.Entry e = entries.get(i);
            sb.append(i == 0 ? "" : ",").append("{\"at\":").append(VixenJson.num(e.at())).append(",\"handoffWidth\":")
                    .append(VixenJson.num(e.handoffWidth())).append(",\"operation\":").append(VixenJson.quote(e.operation())).append('}');
        }
        sb.append("],\"influences\":[");
        for (int i = 0; i < influences.size(); i++) {
            sb.append(i == 0 ? "" : ",").append(VixenJson.influenceText(influences.get(i)));
        }
        sb.append("],\"previous\":").append(VixenJson.quote(previous == null ? "" : previous)).append('}');
        return sha256(sb.toString());
    }

    static String historyChain(String prior,List<Timeline.Entry> entries){
        if(entries.isEmpty()&&prior!=null&&!prior.isEmpty())return prior;
        StringBuilder out=new StringBuilder("[\"vixen-history/1\",");
        out.append(prior==null||prior.isEmpty()?"null":asciiQuote(prior)).append(",[");
        for(int i=0;i<entries.size();i++){
            Timeline.Entry e=entries.get(i);if(i>0)out.append(',');
            out.append('[').append(asciiQuote(String.format("%016x",Double.doubleToRawLongBits(e.at())))).append(',')
                .append(asciiQuote(e.operation())).append(',').append(asciiQuote(String.format("%016x",Double.doubleToRawLongBits(e.handoffWidth())))).append(']');
        }
        return sha256(out.append("]]").toString());
    }
    private static String asciiQuote(String value){
        StringBuilder out=new StringBuilder();
        for(char c:VixenJson.quote(value).toCharArray()){
            if(c>127)out.append(String.format("\\u%04x",(int)c));else out.append(c);
        }
        return out.toString();
    }

    static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * A tail from its parts, with each open fork's future seed derived from the leaf it leaves: what a caller holding a checkpoint and the forks recorded since
     * builds, without a full lineage.
     */
    public static Tail of(Config config, Seed leafSeed, double from, long folded, String witness, List<Timeline.Snapshot.Fork> forks, List<Influence> influences) {
        return of(config,leafSeed,from,folded,witness,forks,influences,false);
    }
    public static Tail of(Config config,Seed leafSeed,double from,long folded,String witness,List<Timeline.Snapshot.Fork> forks,List<Influence> influences,boolean coherent){
        List<Timeline.Entry> open = new java.util.ArrayList<>();
        Seed parent = leafSeed;
        for (Timeline.Snapshot.Fork f : forks) {
            int seed = Hash.forkSeed(parent, f.operation(), f.at());
            open.add(new Timeline.Entry(f.at(), f.operation(), f.handoffWidth(), seed));
            parent = Seed.of(seed & 0xffffffffL);
        }
        return new Tail(config, leafSeed, from, folded, witness, open, influences,coherent);
    }

    /** Rebuilds the timeline from the checkpoint. */
    public Timeline restore() {
        Timeline t = Timeline.resume(config, leafSeed, from, folded, witness,coherent);
        for (Timeline.Entry e : open) {
            t = t.replayFork(e.at(), e.operation(), e.handoffWidth());
        }
        for (Influence inf : influences) {
            t = t.replayInfluence(inf);
        }
        return t;
    }
}
