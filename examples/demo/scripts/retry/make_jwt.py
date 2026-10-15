"""
Prints an Identity Verification JWT, signed ES256 with openssl so no extra packages are needed.
Usage: make_jwt.py <private key pem> <app id> <external id> [label=id] [email:addr] [sms:number]
Aliases, emails and SMS numbers the SDK adds must be claimed in the token.
"""

import base64
import json
import subprocess
import sys
import time

# Identity Verification only accepts tokens whose exp is within an hour of now.
LIFETIME = 3600


def b64(data):
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def der_to_raw(der):
    """openssl emits an ASN.1 DER signature, JWS wants r || s."""
    i = 3 if der[1] & 0x80 else 2
    parts = []
    for _ in range(2):
        length = der[i + 1]
        parts.append(int.from_bytes(der[i + 2:i + 2 + length], "big").to_bytes(32, "big"))
        i += 2 + length
    return b"".join(parts)


def main():
    if len(sys.argv) < 4:
        sys.exit(__doc__.strip())
    key, app_id, external_id, *extra = sys.argv[1:]
    identity = {"external_id": external_id}
    subscriptions = []
    for arg in extra:
        kind, sep, value = arg.partition(":")
        if sep and kind in ("email", "sms"):
            subscriptions.append({"type": "Email" if kind == "email" else "SMS", "token": value})
        elif "=" in arg:
            label, value = arg.split("=", 1)
            identity[label] = value
        else:
            sys.exit(f"unrecognized claim: {arg}")
    claims = {"iss": app_id, "exp": int(time.time()) + LIFETIME, "identity": identity}
    if subscriptions:
        claims["subscriptions"] = subscriptions

    header = b64(json.dumps({"alg": "ES256", "typ": "JWT"}).encode())
    signing_input = f"{header}.{b64(json.dumps(claims).encode())}"
    der = subprocess.run(
        ["openssl", "dgst", "-sha256", "-sign", key],
        input=signing_input.encode(), capture_output=True, check=True,
    ).stdout
    print(f"{signing_input}.{b64(der_to_raw(der))}")


if __name__ == "__main__":
    main()
