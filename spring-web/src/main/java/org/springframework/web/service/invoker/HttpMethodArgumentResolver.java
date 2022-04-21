/*
 * Copyright 2002-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.service.invoker;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.lang.Nullable;

/**
 * An implementation of {@link HttpServiceMethodArgumentResolver} that resolves
 * request HTTP method based on argument type. Arguments of type
 * {@link HttpMethod} will be used to determine the method.
 *
 * @author Olga Maciaszek-Sharma
 * @since 6.0
 */
public class HttpMethodArgumentResolver implements HttpServiceMethodArgumentResolver {

	private static final Log LOG = LogFactory.getLog(HttpMethodArgumentResolver.class);

	@Override
	public void resolve(@Nullable Object argument, MethodParameter parameter,
			HttpRequestDefinition requestDefinition) {
		if (argument == null) {
			return;
		}
		if (argument instanceof HttpMethod httpMethod) {
			if (LOG.isTraceEnabled()) {
				LOG.trace("Resolved HTTP method to: " + httpMethod.name());
			}
			requestDefinition.setHttpMethod(httpMethod);
		}
	}
}
