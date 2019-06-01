/*
 *
 * Copyright (c) 2013 - 2019 Lijun Liao
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.xipki.security;

import org.xipki.util.concurrent.ConcurrentBagEntry;

/**
 * A {@link ConcurrentBagEntry} for {@link XiContentSigner}.
 *
 * @author Lijun Liao
 * @since 2.2.0
 */

public class ConcurrentBagEntrySigner extends ConcurrentBagEntry<XiContentSigner> {

  public ConcurrentBagEntrySigner(XiContentSigner value) {
    super(value);
  }

}
