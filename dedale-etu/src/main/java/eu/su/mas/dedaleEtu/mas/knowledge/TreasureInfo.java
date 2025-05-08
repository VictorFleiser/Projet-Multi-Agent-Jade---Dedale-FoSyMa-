package eu.su.mas.dedaleEtu.mas.knowledge;

import java.io.Serializable;
import java.util.Objects;

public class TreasureInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String type; // "Gold", "Diamond"
    public final int amount;
    public final boolean lockIsOpen;
    public final int requiredLockpicking;
    public final int requiredStrength;
    public final long timestamp;

    public TreasureInfo(String type, int amount, boolean lockIsOpen, int requiredLockpicking, int requiredStrength, long timestamp) {
    	if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("Treasure type cannot be null or empty.");
        }
        this.type = type;
        this.amount = amount;
        this.lockIsOpen = lockIsOpen;
        this.requiredLockpicking = requiredLockpicking;
        this.requiredStrength = requiredStrength;
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return type + "(" + amount + ") [LP:" + requiredLockpicking + ", Str:" + requiredStrength + ", Locked:" + !lockIsOpen + "]";
    }
    
    @Override
    public boolean equals(Object o) {
        // 1. Self check
        if (this == o) return true;
        // 2. Null check and class check
        if (o == null || getClass() != o.getClass()) return false;
        // 3. Cast
        TreasureInfo that = (TreasureInfo) o;
        // 4. Field comparison
        return amount == that.amount &&
               lockIsOpen == that.lockIsOpen &&
               requiredLockpicking == that.requiredLockpicking &&
               requiredStrength == that.requiredStrength &&
               Objects.equals(type, that.type); // for potentially null strings
    }

    @Override
    public int hashCode() {
        // hash code based on fields used in equals
        return Objects.hash(type, amount, lockIsOpen, requiredLockpicking, requiredStrength);
    }
    
    public TreasureInfo updateState(int newAmount, boolean newLockState, long newTimestamp) {
        return new TreasureInfo(this.type, newAmount, newLockState, this.requiredLockpicking, this.requiredStrength, newTimestamp);
    }
}