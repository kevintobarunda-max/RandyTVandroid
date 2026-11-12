#!/usr/bin/env python3
"""Prepara credenciales locales para Randy TV Tizen sin publicarlas en GitHub.

Por defecto copia las constantes SERVER, USERNAME y PASSWORD de Android
(ApiService.kt) al archivo tizen/js/local-config.js. --nuevo permite introducir
credenciales distintas de forma interactiva (la contraseña no se muestra).
"""
import argparse
import getpass
import json
import os
from pathlib import Path
import re
import sys

TIZEN = Path(__file__).resolve().parents[1]
ROOT = TIZEN.parent
SOURCE = ROOT / "app/src/main/java/com/randytv/mobile/data/network/ApiService.kt"
OUTPUT = TIZEN / "js/local-config.js"

def read_android():
    if not SOURCE.is_file():
        raise ValueError("No encuentro ApiService.kt de Android en este repositorio.")
    content = SOURCE.read_text(encoding="utf-8")
    credentials = {}
    for key in ("SERVER", "USERNAME", "PASSWORD"):
        match = re.search(r'const\s+val\s+' + key + r'\s*=\s*"([^"]*)"', content)
        if not match:
            raise ValueError("No se encontró la constante " + key + " en el proyecto Android.")
        credentials[key.lower()] = match.group(1)
    return credentials

def prompt_credentials():
    print("Ingresa los datos de tu proveedor autorizado. No se publicarán en GitHub.")
    return {
        "server": input("Servidor (http[s]://dominio:puerto): ").strip(),
        "username": input("Usuario: ").strip(),
        "password": getpass.getpass("Contraseña (oculta): "),
    }

def main():
    parser = argparse.ArgumentParser(description="Configura Randy TV Samsung para usar credenciales ya cargadas.")
    parser.add_argument("--nuevo", action="store_true", help="Solicita datos nuevos en vez de leerlos desde el código Android")
    args = parser.parse_args()
    values = prompt_credentials() if args.nuevo else read_android()
    values["server"] = values["server"].rstrip("/")
    if not values["server"].startswith(("http://", "https://")) or not values["username"] or not values["password"]:
        raise ValueError("Los datos están incompletos o el servidor no empieza con http:// o https://")
    output = "/* Local: NO SUBIR A GITHUB, ni compartir el .wgt firmado. */\n"
    output += "window.RANDY_LOCAL_CONFIG = " + json.dumps(values, ensure_ascii=False) + ";\n"
    # Crear con permisos restrictivos sin exponer nada en la salida.
    fd = os.open(str(OUTPUT), os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as handle:
        handle.write(output)
    os.chmod(OUTPUT, 0o600)
    print("✓ Credenciales incorporadas localmente a tizen/js/local-config.js")
    print("✓ La aplicación las utilizará al abrirse. No aparecen en este mensaje.")
    print("AVISO: Las credenciales quedan dentro del paquete .wgt (no está cifrado).")
    print("No compartas el .wgt ni lo subas a GitHub.")
    print("Si las credenciales del Android ya se publicaron en GitHub, cámbialas con tu proveedor.")

if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError) as exc:
        print("Error: " + str(exc), file=sys.stderr)
        sys.exit(1)
