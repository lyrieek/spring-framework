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

package org.springframework.web.servlet.mvc.method.annotation;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.lang.Nullable;
import org.springframework.validation.BindException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.util.WebUtils;

/**
 * A class with an {@code @ExceptionHandler} method that handles all Spring MVC
 * raised exceptions by returning a {@link ResponseEntity} with RFC-7807
 * formatted error details in the body.
 *
 * <p>Convenient as a base class of an {@link ControllerAdvice @ControllerAdvice}
 * for global exception handling in an application. Subclasses can override
 * individual methods that handle a specific exception, override
 * {@link #handleExceptionInternal} to override common handling of all exceptions,
 * or {@link #createResponseEntity} to intercept the final step of creating the
 * @link ResponseEntity} from the selected HTTP status code, headers, and body.
 *
 * @author Rossen Stoyanchev
 * @since 3.2
 * @see #handleException(Exception, WebRequest)
 * @see org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver
 */
public abstract class ResponseEntityExceptionHandler {

	/**
	 * Log category to use when no mapped handler is found for a request.
	 * @see #pageNotFoundLogger
	 */
	public static final String PAGE_NOT_FOUND_LOG_CATEGORY = "org.springframework.web.servlet.PageNotFound";

	/**
	 * Specific logger to use when no mapped handler is found for a request.
	 * @see #PAGE_NOT_FOUND_LOG_CATEGORY
	 */
	protected static final Log pageNotFoundLogger = LogFactory.getLog(PAGE_NOT_FOUND_LOG_CATEGORY);

	/**
	 * Common logger for use in subclasses.
	 */
	protected final Log logger = LogFactory.getLog(getClass());


	/**
	 * Handle all exceptions raised within Spring MVC handling of the request .
	 * @param ex the exception to handle
	 * @param request the current request
	 */
	@ExceptionHandler({
			HttpRequestMethodNotSupportedException.class,
			HttpMediaTypeNotSupportedException.class,
			HttpMediaTypeNotAcceptableException.class,
			MissingPathVariableException.class,
			MissingServletRequestParameterException.class,
			MissingServletRequestPartException.class,
			ServletRequestBindingException.class,
			MethodArgumentNotValidException.class,
			NoHandlerFoundException.class,
			AsyncRequestTimeoutException.class,
			ErrorResponseException.class,
			ConversionNotSupportedException.class,
			TypeMismatchException.class,
			HttpMessageNotReadableException.class,
			HttpMessageNotWritableException.class,
			BindException.class
		})
	@Nullable
	public final ResponseEntity<Object> handleException(Exception ex, WebRequest request) throws Exception {
		HttpHeaders headers = new HttpHeaders();

		if (ex instanceof HttpRequestMethodNotSupportedException subEx) {
			return handleHttpRequestMethodNotSupported(subEx, subEx.getHeaders(), subEx.getStatusCode(), request);
		}
		else if (ex instanceof HttpMediaTypeNotSupportedException subEx) {
			return handleHttpMediaTypeNotSupported(subEx, subEx.getHeaders(), subEx.getStatusCode(), request);
		}
		else if (ex instanceof HttpMediaTypeNotAcceptableException subEx) {
			return handleHttpMediaTypeNotAcceptable(subEx, subEx.getHeaders(), subEx.getStatusCode(), request);
		}
		else if (ex instanceof MissingPathVariableException subEx) {
			return handleMissingPathVariable(subEx, subEx.getHeaders(), subEx.getStatusCode(), request);
		}
		else if (ex instanceof MissingServletRequestParameterException subEx) {
			return handleMissingServletRequestParameter(subEx, subEx.getHeaders(), subEx.getStatusCode(), request);
		}
		else if (ex instanceof MissingServletRequestPartException subEx) {
			return handleMissingServletRequestPart(subEx, subEx.getHeaders(), subEx.getStatusCode(), request);
		}
		else if (ex instanceof ServletRequestBindingException subEx) {
			return handleServletRequestBindingException(subEx, subEx.getHeaders(), subEx.getStatusCode(), request);
		}
		else if (ex instanceof MethodArgumentNotValidException subEx) {
			return handleMethodArgumentNotValid(subEx, subEx.getHeaders(), subEx.getStatusCode(), request);
		}
		else if (ex instanceof NoHandlerFoundException subEx) {
			return handleNoHandlerFoundException(subEx, subEx.getHeaders(), subEx.getStatusCode(), request);
		}
		else if (ex instanceof AsyncRequestTimeoutException subEx) {
			return handleAsyncRequestTimeoutException(subEx, subEx.getHeaders(), subEx.getStatusCode(), request);
		}
		else if (ex instanceof ErrorResponse errorEx) {
			return handleExceptionInternal(ex, null, errorEx.getHeaders(), errorEx.getStatusCode(), request);
		}

		// Lower level exceptions, and exceptions used symmetrically on client and server

		if (ex instanceof ConversionNotSupportedException theEx) {
			return handleConversionNotSupported(theEx, headers, HttpStatus.INTERNAL_SERVER_ERROR, request);
		}
		else if (ex instanceof TypeMismatchException theEx) {
			return handleTypeMismatch(theEx, headers, HttpStatus.BAD_REQUEST, request);
		}
		else if (ex instanceof HttpMessageNotReadableException theEx) {
			return handleHttpMessageNotReadable(theEx, headers, HttpStatus.BAD_REQUEST, request);
		}
		else if (ex instanceof HttpMessageNotWritableException theEx) {
			return handleHttpMessageNotWritable(theEx, headers, HttpStatus.INTERNAL_SERVER_ERROR, request);
		}
		else if (ex instanceof BindException theEx) {
			return handleBindException(theEx, headers, HttpStatus.BAD_REQUEST, request);
		}
		else {
			// Unknown exception, typically a wrapper with a common MVC exception as cause
			// (since @ExceptionHandler type declarations also match first-level causes):
			// We only deal with top-level MVC exceptions here, so let's rethrow the given
			// exception for further processing through the HandlerExceptionResolver chain.
			throw ex;
		}
	}

	/**
	 * Customize the handling of {@link HttpRequestMethodNotSupportedException}.
	 * <p>This method logs a warning and delegates to {@link #handleExceptionInternal}.
	 * @param ex the exception to handle
	 * @param headers the headers to use for the response
	 * @param status the status code to use for the response
	 * @param request the current request
	 * @return a {@code ResponseEntity} for the response to use, possibly
	 * {@code null} when the response is already committed
	 */
	@Nullable
	protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
			HttpRequestMethodNotSupportedException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

		pageNotFoundLogger.warn(ex.getMessage());
		return handleExceptionInternal(ex, null, headers, status, request);
	}

	/**
	 * Customize the handling of {@link HttpMediaTypeNotSupportedException}.
	 * <p>This method delegates to {@link #handleExceptionInternal}.
	 * @param ex the exception to handle
	 * @param headers the headers to use for the response
	 * @param status the status code to use for the response
	 * @param request the current request
	 * @return a {@code ResponseEntity} for the response to use, possibly
	 * {@code null} when the response is already committed
	 */
	@Nullable
	protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
			HttpMediaTypeNotSupportedException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

		return handleExceptionInternal(ex, null, headers, status, request);
	}

	/**
	 * Customize the handling of {@link HttpMediaTypeNotAcceptableException}.
	 * <p>This method delegates to {@link #handleExceptionInternal}.
	 * @param ex the exception to handle
	 * @param headers the headers to use for the response
	 * @param status the status code to use for the response
	 * @param request the current request
	 * @return a {@code ResponseEntity} for the response to use, possibly
	 * {@code null} when the response is already committed
	 */
	@Nullable
	protected ResponseEntity<Object> handleHttpMediaTypeNotAcceptable(
			HttpMediaTypeNotAcceptableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

		return handleExceptionInternal(ex, null, headers, status, request);
	}

	/**
	 * Customize the handling of {@link MissingPathVariableException}.
	 * <p>This method delegates to {@link #handleExceptionInternal}.
	 * @param ex the exception to handle
	 * @param headers the headers to use for the response
	 * @param status the status code to use for the response
	 * @param request the current request
	 * @return a {@code ResponseEntity} for the response to use, possibly
	 * {@code null} when the response is already committed
	 * @since 4.2
	 */
	@Nullable
	protected ResponseEntity<Object> handleMissingPathVariable(
			MissingPathVariableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

		return handleExceptionInternal(ex, null, headers, status, request);
	}

	/**
	 * Customize the handling of {@link MissingServletRequestParameterException}.
	 * <p>This method delegates to {@link #handleExceptionInternal}.
	 * @param ex the exception to handle
	 * @param headers the headers to use for the response
	 * @param status the status code to use for the response
	 * @param request the current request
	 * @return a {@code ResponseEntity} for the response to use, possibly
	 * {@code null} when the response is already committed
	 */
	@Nullable
	protected ResponseEntity<Object> handleMissingServletRequestParameter(
			MissingServletRequestParameterException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

		return handleExceptionInternal(ex, null, headers, status, request);
	}

	/**
	 * Customize the handling of {@link MissingServletRequestPartException}.
	 * <p>This method delegates to {@link #handleExceptionInternal}.
	 * @param ex the exception to handle
	 * @param headers the headers to use for the response
	 * @param status the status code to use for the response
	 * @param request the current request
	 * @return a {@code ResponseEntity} for the response to use, possibly
	 * {@code null} when the response is already committed
	 */
	@Nullable
	protected ResponseEntity<Object> handleMissingServletRequestPart(
			MissingServletRequestPartException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

		return handleExceptionInternal(ex, null, headers, status, request);
	}

	/**
	 * Customize the handling of {@link ServletRequestBindingException}.
	 * <p>This method delegates to {@link #handleExceptionInternal}.
	 * @param ex the exception to handle
	 * @param headers the headers to use for the response
	 * @param status the status code to use for the response
	 * @param request the current request
	 * @return a {@code ResponseEntity} for the response to use, possibly
	 * {@code null} when the response is already committed
	 */
	@Nullable
	protected ResponseEntity<Object> handleServletRequestBindingException(
			ServletRequestBindingException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

		return handleExceptionInternal(ex, null, headers, status, request);
	}

	/**
	 * Customize the handling of {@link MethodArgumentNotValidException}.
	 * <p>This method delegates to {@link #handleExceptionInternal}.
	 * @param ex the exception to handle
	 * @param headers the headers to be written to the response
	 * @param status the selected response status
	 * @param request the current request
	 * @return a {@code ResponseEntity} for the response to use, possibly
	 * {@code null} when the response is already committed
	 */
	@Nullable
	protected ResponseEntity<Object> handleMethodArgumentNotValid(
			MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

		return handleExceptionInternal(ex, null, headers, status, request);
	}

	/**
	 * Customize the handling of {@link NoHandlerFoundException}.
	 * <p>This method delegates to {@link #handleExceptionInternal}.
	 * @param ex the exception to handle
	 * @param headers the headers to use for the response
	 * @param status the status code to use for the response
	 * @param request the current request
	 * @return a {@code ResponseEntity} for the response to use, possibly
	 * {@code null} when the response is already committed
	 * @since 4.0
	 */
	@Nullable
	protected ResponseEntity<Object> handleNoHandlerFoundException(
			NoHandlerFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

		return handleExceptionInternal(ex, null, headers, status, request);
	}

	/**
	 * Customize the handling of {@link AsyncRequestTimeoutException}.
	 * <p>This method delegates to {@link #handleExceptionInternal}.
	 * @param ex the exception to handle
	 * @param headers the headers to use for the response
	 * @param status the status code to use for the response
	 * @param request the current request
	 * @return a {@code ResponseEntity} for the response to use, possibly
	 * {@code null} when the response is already committed
	 * @since 4.2.8
	 */
	@Nullable
	protected ResponseEntity<Object> handleAsyncRequestTimeoutException(
			AsyncRequestTimeoutException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

		return handleExceptionInternal(ex, null, headers, status, request);
	}

	/**
	 * Customize the handling of {@link ConversionNotSupportedException}.
	 * <p>By default this method creates a {@link ProblemDetail} with the status
	 * and a short detail message, and then delegates to
	 * {@link #handleExceptionInternal}.
	 * @param ex the exception to handle
	 * @param headers the headers to use for the response
	 * @param status the status code to use for the response
	 * @param request the current request
	 * @return a {@code ResponseEntity} for the response to use, possibly
	 * {@code null} when the response is already committed
	 */
	@Nullable
	protected ResponseEntity<Object> handleConversionNotSupported(
			ConversionNotSupportedException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

		ProblemDetail body = ProblemDetail.forStatusAndDetail(status,
				"Failed to convert '" + ex.getPropertyName() + "' with value: '" + ex.getValue() + "'");

		return handleExceptionInternal(ex, body, headers, status, request);
	}

	/**
	 * Customize the handling of {@link TypeMismatchException}.
	 * <p>By default this method creates a {@link ProblemDetail} with the status
	 * and a short detail message, and then delegates to
	 * {@link #handleExceptionInternal}.
	 * @param ex the exception to handle
	 * @param headers the headers to use for the response
	 * @param status the status code to use for the response
	 * @param request the current request
	 * @return a {@code ResponseEntity} for the response to use, possibly
	 * {@code null} when the response is already committed
	 */
	@Nullable
	protected ResponseEntity<Object> handleTypeMismatch(
			TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

		ProblemDetail body = ProblemDetail.forStatusAndDetail(status,
				"Unexpected type for '" + ex.getPropertyName() + "' with value: '" + ex.getValue() + "'");

		return handleExceptionInternal(ex, body, headers, status, request);
	}

	/**
	 * Customize the handling of {@link HttpMessageNotReadableException}.
	 * <p>By default this method creates a {@link ProblemDetail} with the status
	 * and a short detail message, and then delegates to
	 * {@link #handleExceptionInternal}.
	 * @param ex the exception to handle
	 * @param headers the headers to use for the response
	 * @param status the status code to use for the response
	 * @param request the current request
	 * @return a {@code ResponseEntity} for the response to use, possibly
	 * {@code null} when the response is already committed
	 */
	@Nullable
	protected ResponseEntity<Object> handleHttpMessageNotReadable(
			HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

		ProblemDetail body = ProblemDetail.forStatusAndDetail(status, "Failed to read request body");
		return handleExceptionInternal(ex, body, headers, status, request);
	}

	/**
	 * Customize the handling of {@link HttpMessageNotWritableException}.
	 * <p>By default this method creates a {@link ProblemDetail} with the status
	 * and a short detail message, and then delegates to
	 * {@link #handleExceptionInternal}.
	 * @param ex the exception to handle
	 * @param headers the headers to use for the response
	 * @param status the status code to use for the response
	 * @param request the current request
	 * @return a {@code ResponseEntity} for the response to use, possibly
	 * {@code null} when the response is already committed
	 */
	@Nullable
	protected ResponseEntity<Object> handleHttpMessageNotWritable(
			HttpMessageNotWritableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

		ProblemDetail body = ProblemDetail.forStatusAndDetail(status, "Failed to write response body");
		return handleExceptionInternal(ex, body, headers, status, request);
	}

	/**
	 * Customize the handling of {@link BindException}.
	 * <p>By default this method creates a {@link ProblemDetail} with the status
	 * and a short detail message, and then delegates to
	 * {@link #handleExceptionInternal}.
	 * @param ex the exception to handle
	 * @param headers the headers to use for the response
	 * @param status the status code to use for the response
	 * @param request the current request
	 * @return a {@code ResponseEntity} for the response to use, possibly
	 * {@code null} when the response is already committed
	 */
	@Nullable
	protected ResponseEntity<Object> handleBindException(
			BindException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

		ProblemDetail body = ProblemDetail.forStatusAndDetail(status, "Failed to bind request");
		return handleExceptionInternal(ex, body, headers, status, request);
	}

	/**
	 * Internal handler method that all others in this class delegate to, for
	 * common handling, and for the creation of a {@link ResponseEntity}.
	 * <p>The default implementation does the following:
	 * <ul>
	 * <li>return {@code null} if response is already committed
	 * <li>set the {@code "jakarta.servlet.error.exception"} request attribute
	 * if the response status is 500 (INTERNAL_SERVER_ERROR).
	 * <li>extract the {@link ErrorResponse#getBody() body} from
	 * {@link ErrorResponse} exceptions, if the {@code body} is {@code null}.
	 * </ul>
	 * @param ex the exception to handle
	 * @param body the body to use for the response
	 * @param headers the headers to use for the response
	 * @param statusCode the status code to use for the response
	 * @param request the current request
	 * @return a {@code ResponseEntity} for the response to use, possibly
	 * {@code null} when the response is already committed
	 */
	@Nullable
	protected ResponseEntity<Object> handleExceptionInternal(
			Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {

		if (request instanceof ServletWebRequest servletWebRequest) {
			HttpServletResponse response = servletWebRequest.getResponse();
			if (response != null && response.isCommitted()) {
				if (logger.isWarnEnabled()) {
					logger.warn("Response already committed. Ignoring: " + ex);
				}
				return null;
			}
		}

		if (statusCode.equals(HttpStatus.INTERNAL_SERVER_ERROR)) {
			request.setAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE, ex, WebRequest.SCOPE_REQUEST);
		}

		if (body == null && ex instanceof ErrorResponse errorResponse) {
			body = errorResponse.getBody();
		}

		return createResponseEntity(body, headers, statusCode, request);
	}

	/**
	 * Create the {@link ResponseEntity} to use from the given body, headers,
	 * and statusCode. Subclasses can override this method to inspect and possibly
	 * modify the body, headers, or statusCode, e.g. to re-create an instance of
	 * {@link ProblemDetail} as an extension of {@link ProblemDetail}.
	 * @param body the body to use for the response
	 * @param headers the headers to use for the response
	 * @param statusCode the status code to use for the response
	 * @param request the current request
	 * @return the {@code ResponseEntity} instance to use
	 * @since 6.0
	 */
	protected ResponseEntity<Object> createResponseEntity(
			@Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {

		return new ResponseEntity<>(body, headers, statusCode);
	}

}
