// SPDX-License-Identifier: Apache-2.0

package it.frob.sleighidea.psi.impl;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import it.frob.sleighidea.SleighFileType;
import it.frob.sleighidea.SleighLanguage;
import it.frob.sleighidea.model.ModelException;
import it.frob.sleighidea.model.space.Space;
import it.frob.sleighidea.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Concrete implementation of {@link SleighFile}.
 */
public class SleighFileImpl extends PsiFileBase implements SleighFile, PsiNameIdentifierOwner {

    /**
     * All the {@code token} elements in the file, wrapped in a cache-aware container.
     */
    private final CachedValue<List<SleighTokenDefinition>> tokens = createCachedValue(
            new ValueProvider<>() {
                @Override
                protected @NotNull List<SleighTokenDefinition> computeValue() {
                    return Collections.unmodifiableList(collectTokens());
                }
            }
    );

    /**
     * All the {@code macro} elements in the file, wrapped in a cache-aware container.
     */
    private final CachedValue<List<SleighMacrodef>> macros = createCachedValue(
            new ValueProvider<>() {
                @Override
                protected @NotNull List<SleighMacrodef> computeValue() {
                    return Collections.unmodifiableList(collectMacros());
                }
            }
    );

    /**
     * All the {@code space} elements in the file, wrapped in a cache-aware linear container.
     */
    private final CachedValue<List<Space>> spacesList = createCachedValue(
            new ValueProvider<>() {
                @Override
                protected @NotNull List<Space> computeValue() {
                    return Collections.unmodifiableList(collectSpacesList());
                }
            }
    );

    /**
     * All the {@code space} elements in the file, wrapped in a cache-aware container.
     */
    private final CachedValue<Map<String, Space>> spacesMap = createCachedValue(
            new ValueProvider<>() {
                @Override
                protected @NotNull Map<String, Space> computeValue() {
                    return Collections.unmodifiableMap(buildSpacesMap(spacesList.getValue()));
                }
            }
    );

    /**
     * All the {@code include} elements in the file, wrapped in a cache-aware container.
     */
    private final CachedValue<List<SleighInclude>> includes = createCachedValue(
            new ValueProvider<>() {
                @Override
                protected @NotNull List<SleighInclude> computeValue() {
                    return Collections.unmodifiableList(collectIncludes());
                }
            }
    );

    /**
     * Constructor.
     *
     * @param viewProvider the access class for the file's PSI elements.
     */
    public SleighFileImpl(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, SleighLanguage.INSTANCE);
    }

    @Override
    public @NotNull FileType getFileType() {
        return SleighFileType.INSTANCE;
    }

    @Override
    public String toString() {
        return "Sleigh File";
    }

    @Override
    public @Nullable PsiElement getNameIdentifier() {
        return null;
    }

    @Override
    public Collection<SleighTokenDefinition> getTokens() {
        return tokens.getValue();
    }

    @Override
    public Collection<SleighMacrodef> getMacros() {
        return macros.getValue();
    }

    @Override
    public @Nullable Space getSpaceForName(@NotNull String name) {
        return spacesMap.getValue().getOrDefault(name, null);
    }

    @Override
    public Collection<Space> getSpaces() {
        return spacesList.getValue();
    }

    @Override
    public Collection<SleighInclude> getIncludes() {
        return includes.getValue();
    }

    /**
     * Create a cached value.
     *
     * @param provider the cache-aware value provider to get data from.
     * @param <T>      the class of the value to cache.
     * @return a {@link CachedValue} accessor instance.
     */
    @NotNull
    private <T> CachedValue<T> createCachedValue(@NotNull CachedValueProvider<T> provider) {
        return CachedValuesManager.getManager(getProject()).createCachedValue(provider, false);
    }

    /**
     * Cached value provider base class.
     *
     * @param <T> the class of the value that will be provided.
     */
    private abstract class ValueProvider<T> implements CachedValueProvider<T> {
        @NotNull
        @Override
        public final Result<T> compute() {
            return Result.create(computeValue(), SleighFileImpl.this);
        }

        /**
         * Compute the contained value.
         *
         * @return the current value to cache in the provider accessor methods.
         */
        @Nullable
        protected abstract T computeValue();
    }

    /**
     * Extract all {@code token} elements in the file.
     *
     * @return a list containing the {@link SleighTokenDefinition} instances found in the file.
     */
    @NotNull
    private List<SleighTokenDefinition> collectTokens() {
        return new ArrayList<>(PsiTreeUtil.collectElementsOfType(this, SleighTokenDefinition.class));
    }

    /**
     * Extract all {@code macro} elements in the file.
     *
     * @return a list containing the {@link SleighMacrodef} instances found in the file.
     */
    @NotNull
    private List<SleighMacrodef> collectMacros() {
        return new ArrayList<>(PsiTreeUtil.collectElementsOfType(this, SleighMacrodef.class));
    }

    /**
     * Extract all {@code space} elements in the file.
     *
     * @return a map containing the {@link SleighSpaceDefinition} instances found in the file, wrapped in their
     * respective {@link Space} model container classes, indexed by their name.
     */
    @NotNull
    private List<Space> collectSpacesList() {
        Collection<SleighVarnodedef> variableDeclarations =
                PsiTreeUtil.collectElementsOfType(this, SleighVarnodedef.class);
        return PsiTreeUtil.collectElementsOfType(this, SleighSpaceDefinition.class)
                .stream()
                .map(space -> {
                    try {
                        return new Space(space, variableDeclarations);
                    } catch (ModelException ignored) {
                        // TODO: Figure out how to handle this case.
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Build a map out of the given {@link Space} list container.
     *
     * @param spaces the instances to build a map with.
     * @return a map containing the given instances, indexed by their assigned name.
     */
    @NotNull
    private Map<String, Space> buildSpacesMap(@NotNull List<Space> spaces) {
        Map<String, Space> spacesMap = new HashMap<>();

        spaces.forEach(space -> {
            if (spacesMap.containsKey(space.getName())) {
                // TODO: Figure out how to handle duplicate spaces.
                return;
            }
            spacesMap.put(space.getName(), space);
        });

        return spacesMap;
    }

    /**
     * Extract all {@code include} elements in the file.
     *
     * @return a list containing the {@link SleighInclude} instances found in the file.
     */
    @NotNull
    private List<SleighInclude> collectIncludes() {
        return new ArrayList<>(PsiTreeUtil.collectElementsOfType(this, SleighInclude.class));
    }
}
