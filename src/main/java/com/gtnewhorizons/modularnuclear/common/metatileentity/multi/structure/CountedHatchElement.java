package com.gtnewhorizons.modularnuclear.common.metatileentity.multi.structure;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.ToLongFunction;

import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.util.IGTHatchAdder;

/**
 * A kind of hatch a multiblock takes, for GregTech's HatchElementBuilder: which hatch classes it is, how the
 * controller adds one, and how many the controller has. HatchElementBuilder.atLeast places one in NEI's structure
 * preview and in survival autobuild while the count is short, so the count must read the same lists the adder fills.
 */
public final class CountedHatchElement<T> implements IHatchElement<T> {

    private final String name;
    private final IGTHatchAdder<? super T> adder;
    private final ToLongFunction<T> count;
    private final List<Class<? extends IMetaTileEntity>> mteClasses;

    @SafeVarargs
    public CountedHatchElement(String name, IGTHatchAdder<? super T> adder, ToLongFunction<T> count,
        Class<? extends IMetaTileEntity>... mteClasses) {
        this.name = name;
        this.adder = adder;
        this.count = count;
        this.mteClasses = Collections.unmodifiableList(Arrays.asList(mteClasses));
    }

    @Override
    public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
        return mteClasses;
    }

    @Override
    public IGTHatchAdder<? super T> adder() {
        return adder;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public long count(T t) {
        return count.applyAsLong(t);
    }
}
