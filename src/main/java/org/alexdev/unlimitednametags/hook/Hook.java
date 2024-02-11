package org.alexdev.unlimitednametags.hook;

import lombok.RequiredArgsConstructor;
import org.alexdev.unlimitednametags.UnlimitedNameTags;

@RequiredArgsConstructor
public abstract class Hook {

    protected final UnlimitedNameTags plugin;

    public abstract void onEnable();

    public abstract void onDisable();
}
