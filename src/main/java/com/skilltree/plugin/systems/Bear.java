package com.skilltree.plugin.systems;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;


public enum Bear {
    POLAR_BEAR(EntityType.POLAR_BEAR),
    GRIZZLY_BEAR(EntityType.WOLF),
    BLACK_BEAR(EntityType.WOLF),
    PANDA(EntityType.PANDA);

    private final EntityType entityType;

    Bear(EntityType entityType) {
        this.entityType = entityType;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public void setCustomName(String string) {
        // This method is part of the Nameable interface
        // It's better to implement this in a separate class if you need more logic
        throw new UnsupportedOperationException("This method should be implemented in a custom Nameable class, not here.");
    }

    public void setCustomNameVisible(boolean b) {
        // Same as above
        throw new UnsupportedOperationException("This method should be implemented in a custom Nameable class, not here.");
    }

    public Object getAttribute(Attribute attribute) {
        // This method is part of the AttributeHolder interface
        // It's better to implement this in a separate class if you need more logic
        throw new UnsupportedOperationException("This method should be implemented in a custom AttributeHolder class, not here.");
    }

    public void setHealth(Object attribute) {
        // This method is part of the Damageable interface
        // It's better to implement this in a separate class if you need more logic
        throw new UnsupportedOperationException("This method should be implemented in a custom Damageable class, not here.");
    }
}
