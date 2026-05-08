/*
 * Copyright 2024 https://dejvokep.dev/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.momirealms.sparrow.yaml.engine;

import org.jetbrains.annotations.NotNull;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.constructor.StandardConstructor;
import org.snakeyaml.engine.v2.nodes.Node;

import java.util.HashMap;
import java.util.Map;

public class ExtendedConstructor extends StandardConstructor {
    private final Map<Node, Object> constructed = new HashMap<>();

    public ExtendedConstructor(@NotNull LoadSettings settings) {
        super(settings);
    }

    @Override
    protected Object construct(Node node) {
        Object o = super.construct(node);
        constructed.put(node, o);
        return o;
    }

    @Override
    protected Object constructObjectNoCheck(Node node) {
        Object o = super.constructObjectNoCheck(node);
        constructed.put(node, o);
        return o;
    }

    /**
     * 获取构造器构造完成之后的 Node 的值
     * @param node 目标 Node
     * @return Node 的解析值
     */
    @NotNull
    public Object getConstructed(@NotNull Node node) {
        return constructed.get(node);
    }

    public void clear() {
        constructed.clear();
    }

}
