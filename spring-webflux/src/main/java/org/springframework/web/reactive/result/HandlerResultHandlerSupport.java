/*
 * Copyright 2002-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.reactive.result;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.core.Ordered;
import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.HandlerResult;
import org.springframework.web.reactive.accept.RequestedContentTypeResolver;
import org.springframework.web.server.ServerWebExchange;

/**
 * Base class for {@link org.springframework.web.reactive.HandlerResultHandler
 * HandlerResultHandler} with support for content negotiation and access to a
 * {@code ReactiveAdapter} registry.
 *
 * @author Rossen Stoyanchev
 * @since 5.0
 */
public abstract class HandlerResultHandlerSupport implements Ordered {

	private static final MediaType MEDIA_TYPE_APPLICATION_ALL = new MediaType("application");


	private final RequestedContentTypeResolver contentTypeResolver;

	private final ReactiveAdapterRegistry adapterRegistry;

	private int order = LOWEST_PRECEDENCE;


	protected HandlerResultHandlerSupport(RequestedContentTypeResolver contentTypeResolver,
			ReactiveAdapterRegistry adapterRegistry) {

		Assert.notNull(contentTypeResolver, "RequestedContentTypeResolver is required");
		Assert.notNull(adapterRegistry, "ReactiveAdapterRegistry is required");
		this.contentTypeResolver = contentTypeResolver;
		this.adapterRegistry = adapterRegistry;
	}


	/**
	 * Return the configured {@link ReactiveAdapterRegistry}.
	 */
	public ReactiveAdapterRegistry getAdapterRegistry() {
		return this.adapterRegistry;
	}

	/**
	 * Return the configured {@link RequestedContentTypeResolver}.
	 */
	public RequestedContentTypeResolver getContentTypeResolver() {
		return this.contentTypeResolver;
	}

	/**
	 * Set the order for this result handler relative to others.
	 * <p>By default set to {@link Ordered#LOWEST_PRECEDENCE}, however see
	 * Javadoc of sub-classes which may change this default.
	 * @param order the order
	 */
	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getOrder() {
		return this.order;
	}


	/**
	 * Get a {@code ReactiveAdapter} for the top-level return value type.
	 * @return the matching adapter or {@code null}
	 */
	@Nullable
	protected ReactiveAdapter getAdapter(HandlerResult result) {
		Class<?> returnType = result.getReturnType().getRawClass();
		return getAdapterRegistry().getAdapter(returnType, result.getReturnValue());
	}

	/**
	 * Select the best media type for the current request through a content
	 * negotiation algorithm.
	 * @param exchange the current request
	 * @param producibleTypesSupplier the media types that can be produced for the current request
	 * @return the selected media type or {@code null}
	 */
	@Nullable
	protected MediaType selectMediaType(ServerWebExchange exchange,
			Supplier<List<MediaType>> producibleTypesSupplier) {

		List<MediaType> acceptableTypes = getAcceptableTypes(exchange);
		List<MediaType> producibleTypes = getProducibleTypes(exchange, producibleTypesSupplier);

		Set<MediaType> compatibleMediaTypes = new LinkedHashSet<>();
		for (MediaType acceptable : acceptableTypes) {
			for (MediaType producible : producibleTypes) {
				if (acceptable.isCompatibleWith(producible)) {
					compatibleMediaTypes.add(selectMoreSpecificMediaType(acceptable, producible));
				}
			}
		}

		List<MediaType> result = new ArrayList<>(compatibleMediaTypes);
		MediaType.sortBySpecificityAndQuality(result);

		for (MediaType mediaType : result) {
			if (mediaType.isConcrete()) {
				return mediaType;
			}
			else if (mediaType.equals(MediaType.ALL) || mediaType.equals(MEDIA_TYPE_APPLICATION_ALL)) {
				return MediaType.APPLICATION_OCTET_STREAM;
			}
		}

		return null;
	}

	private List<MediaType> getAcceptableTypes(ServerWebExchange exchange) {
		List<MediaType> mediaTypes = getContentTypeResolver().resolveMediaTypes(exchange);
		return (mediaTypes.isEmpty() ? Collections.singletonList(MediaType.ALL) : mediaTypes);
	}

	@SuppressWarnings("unchecked")
	private List<MediaType> getProducibleTypes(ServerWebExchange exchange,
			Supplier<List<MediaType>> producibleTypesSupplier) {

		MediaType contentType = exchange.getResponse().getHeaders().getContentType();
		if (contentType != null && contentType.isConcrete()) {
			return Collections.singletonList(contentType);
		}
		Set<MediaType> mediaTypes = exchange.getAttribute(HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE);
		return (mediaTypes != null ? new ArrayList<>(mediaTypes) : producibleTypesSupplier.get());
	}

	private MediaType selectMoreSpecificMediaType(MediaType acceptable, MediaType producible) {
		producible = producible.copyQualityValue(acceptable);
		Comparator<MediaType> comparator = MediaType.SPECIFICITY_COMPARATOR;
		return (comparator.compare(acceptable, producible) <= 0 ? acceptable : producible);
	}

}
