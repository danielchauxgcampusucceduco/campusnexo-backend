package edu.campusnexo.domain.vision;

/** Object classes the edge worker reports; anything else is discarded at the adapter. */
public enum DetectedObjectClass {
    PERSON,
    LAPTOP,
    BACKPACK,
    BOOK;

    /** Belongings that can hold a desk without a person. */
    public boolean isBelonging() {
        return this != PERSON;
    }
}
