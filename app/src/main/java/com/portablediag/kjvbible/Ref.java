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

    public String label(Bible bible) {
        return bible.book(book).name + " " + chapter + ":" + verse;
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
