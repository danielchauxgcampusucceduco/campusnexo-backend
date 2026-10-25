# ADR 0011: Administración solo desde la VPN de la universidad

- Estado: aceptada
- Fecha: 2026-10-24

## Contexto

Varias personas de la administración necesitan acceso a datos de personas, inventario y herramientas de IA. El backend corre en Render y el tablero en Vercel, servicios públicos fuera de una red privada. Una contraseña filtrada no debería bastar para administrar.

## Decisión

- Una VPC en AWS con un servidor WireGuard (EC2 t4g.nano) y una IP elástica (`deploy/aws/vpn-administracion.yaml`). Cada administrador tiene su propia configuración, cifrada en SSM Parameter Store, y sale a Internet por esa IP.
- `AdminNetworkFilter` atiende `/api/v1/admin/**` solo desde las redes de `ADMIN_ALLOWED_NETWORKS`, **además** del rol ADMIN.
- La IP del cliente es la entrada de `X-Forwarded-For` que agregó el último proxy de confianza (`TRUSTED_PROXIES`, 1 en Render), nunca la más a la izquierda, que escribe el cliente. La misma regla alimenta los límites de peticiones.
- El navegador llama directo al backend (no por un proxy de Vercel) para que el backend vea la IP de la VPN.

## Consecuencias

- Una sesión de ADMIN robada no administra desde fuera de la VPN.
- Cuesta unos US$4–5 al mes (EC2 con IP elástica). AWS Client VPN gestionado costaría unos US$75–110 al mes.
- Si Render cambia su número de proxies, hay que ajustar `TRUSTED_PROXIES`; el mensaje del 403 muestra la dirección que vio el servidor para diagnosticarlo.

## Alternativas consideradas

- Desplegar el backend dentro de la VPC: requiere salir de Render.
- Lista de IP de las casas de los administradores: frágil e imposible de mantener.
