/**
 * PermissionsEx
 * Copyright (C) zml and PermissionsEx contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ninja.leaping.permissionsex.sponge;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import ninja.leaping.permissionsex.data.CalculatedSubject;
import ninja.leaping.permissionsex.sponge.option.OptionSubject;
import org.spongepowered.api.service.permission.Subject;
import org.spongepowered.api.service.permission.context.Context;
import org.spongepowered.api.service.permission.context.ContextCalculator;
import org.spongepowered.api.util.Tristate;
import org.spongepowered.api.util.command.CommandSource;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ninja.leaping.permissionsex.sponge.PEXOptionSubjectData.parSet;

/**
 * Permissions subject implementation
 */
public class PEXSubject implements OptionSubject {
    private final PEXSubjectCollection collection;
    private final PEXOptionSubjectData data;
    private final PEXOptionSubjectData transientData;
    private final CalculatedSubject baked;
    private final String identifier;

    public PEXSubject(String identifier, CalculatedSubject baked, PEXOptionSubjectData data, PEXOptionSubjectData transientData, PEXSubjectCollection collection) {
        this.identifier = identifier;
        this.data = data;
        this.transientData = transientData;
        this.baked = baked;
        this.collection = collection;
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    private String identifyUser() {
        final Optional<CommandSource> source = getCommandSource();
        return getIdentifier() + (source.isPresent() ? "/" + source.get().getName() : "");
    }

    @Override
    public Optional<CommandSource> getCommandSource() {
        return getContainingCollection().getCommandSource(this.identifier);
    }

    @Override
    public PEXSubjectCollection getContainingCollection() {
        return this.collection;
    }

    @Override
    public PEXOptionSubjectData getData() {
        return data;
    }

    @Override
    public PEXOptionSubjectData getTransientData() {
        return transientData;
    }

    @Override
    public Optional<String> getOption(Set<Context> contexts, String key) {
        Preconditions.checkNotNull(contexts, "contexts");
        Preconditions.checkNotNull(key, "key");
        final String val = baked.getOptions(parSet(contexts)).get(key);
        if (collection.getPlugin().getManager().hasDebugMode()) {
            collection.getPlugin().getLogger().info("Option " + key + " checked in " + contexts + " for user " + identifyUser() + ": " + val);
        }
        return Optional.fromNullable(val);
    }

    @Override
    public Optional<String> getOption(String key) {
        return getOption(getActiveContexts(), key);
    }

    @Override
    public boolean hasPermission(Set<Context> contexts, String permission) {
        return getPermissionValue(contexts, permission).asBoolean();
    }

    @Override
    public boolean hasPermission(String permission) {
        return hasPermission(getActiveContexts(), permission);
    }

    @Override
    public Tristate getPermissionValue(Set<Context> contexts, String permission) {
        Preconditions.checkNotNull(contexts, "contexts");
        Preconditions.checkNotNull(permission, "permission");

        int ret = baked.getPermissions(parSet(contexts)).get(permission);
        if (collection.getPlugin().getManager().hasDebugMode()) {
            collection.getPlugin().getLogger().info("Permission " + permission + " checked in " + contexts + " for user " + identifyUser() + ": " + ret);
        }
        return ret == 0 ? Tristate.UNDEFINED : ret > 0 ? Tristate.TRUE : Tristate.FALSE;
    }


    @Override
    public boolean isChildOf(Subject parent) {
        return isChildOf(getActiveContexts(), parent);
    }

    @Override
    public boolean isChildOf(Set<Context> contexts, Subject parent) {
        Preconditions.checkNotNull(contexts, "contexts");
        Preconditions.checkNotNull(parent, "parent");
        return getParents(contexts).contains(parent);
    }

    @Override
    public Set<Context> getActiveContexts() {
        Set<Context> set = new HashSet<>();
        for (ContextCalculator calc : this.collection.getPlugin().getContextCalculators()) {
            calc.accumulateContexts(this, set);
        }
        return Collections.unmodifiableSet(set);
    }

    @Override
    public List<Subject> getParents() {
        return getParents(getActiveContexts());
    }

    @Override
    public List<Subject> getParents(final Set<Context> contexts) {
        Preconditions.checkNotNull(contexts, "contexts");
        final List<Map.Entry<String, String>> parents = baked.getParents(parSet(contexts));
        if (collection.getPlugin().getManager().hasDebugMode()) {
            collection.getPlugin().getLogger().info("Parents checked in " + contexts + " for user " + identifyUser() + ": " + parents);
        }
        return Lists.transform(parents, new Function<Map.Entry<String, String>, Subject>() {
            @Nullable
            @Override
            public Subject apply(Map.Entry<String, String> input) {
                return collection.getPlugin().getSubjects(input.getKey()).get().get(input.getValue());
            }
        });
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof PEXSubject)) {
            return false;
        }

        PEXSubject otherSubj = (PEXSubject) other;

        return this.identifier.equals(otherSubj.identifier)
                && this.data.equals(otherSubj.data);
    }
}