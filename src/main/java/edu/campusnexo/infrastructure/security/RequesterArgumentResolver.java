package edu.campusnexo.infrastructure.security;

import edu.campusnexo.application.port.in.Requester;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Lets a controller method declare a {@link Requester} parameter: the authenticated caller, translated from
 * the security principal into the application's own type, so the use cases never see the security package.
 */
final class RequesterArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return Requester.class.equals(parameter.getParameterType());
    }

    @Override
    public Requester resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                     NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        Object attribute = request == null ? null : request.getAttribute(Principal.REQUEST_ATTRIBUTE);
        if (!(attribute instanceof Principal principal)) {
            throw new IllegalStateException("La solicitud llegó al controlador sin un llamador autenticado");
        }
        return new Requester(principal.name(), principal.role() == Role.ADMIN);
    }
}
