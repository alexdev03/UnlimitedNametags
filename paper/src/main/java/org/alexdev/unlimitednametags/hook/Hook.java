package org.alexdev.unlimitednametags.hook;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor
@Getter
public abstract class Hook {

    @NotNull
    protected final UnlimitedNameTags plugin;

    public abstract void onEnable();

    public abstract void onDisable();
}
