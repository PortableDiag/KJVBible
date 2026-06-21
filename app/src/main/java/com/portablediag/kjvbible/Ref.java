package com.portablediag.kjvbible;

/** A single verse reference: 1-based book index, chapter, verse. */
public final class Ref implements Comparable<Ref> {
    public final int book;
    public final int chapter;
    public final int verse;

    public Ref(int book, int chapter, int verse) {
        this.book = book;
        this.chapter = chapter;
        this.verse = verse;
    }

    /** True for a whole-chapter bookmark (verse == 0). */
    public boolean isChapter() {
        return verse == 0;
    }

    public String label(Bible bible) {
        String name = bible.book(book).name;
        return verse > 0 ? name + " " + chapter + ":" + verse : name + " " + chapter;
    }

    public long key() {
        return ((long) book << 24) | ((long) chapter << 12) | verse;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Ref)) return false;
        Ref r = (Ref) o;
        return book == r.book && chapter == r.chapter && verse == r.verse;
    }

    @Override
    public int hashCode() {
        return (int) key();
    }

    @Override
    public int compareTo(Ref o) {
        return Long.compare(key(), o.key());
    }
}
