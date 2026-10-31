#!/usr/bin/env python3
"""Create a local Klip signing key, then store it as GitHub Actions secrets."""
import base64
import getpass
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

REPO = 'Avapage/Klip'
ALIAS = 'klip'


def run():
    for command in ('keytool', 'gh'):
        if not shutil.which(command):
            sys.exit(f'{command} was not found. Install JDK 17 and GitHub CLI first.')
    subprocess.run(['gh', 'auth', 'status'], check=True)
    existing = subprocess.run(['gh', 'secret', 'list', '--repo', REPO, '--json', 'name'],
                              check=True, capture_output=True, text=True)
    signing_names = {'KLIP_KEYSTORE_BASE64', 'KLIP_KEYSTORE_PASSWORD', 'KLIP_KEY_ALIAS', 'KLIP_KEY_PASSWORD'}
    if signing_names.intersection(item['name'] for item in json.loads(existing.stdout)):
        sys.exit('Signing secrets already exist on GitHub. Stopping to avoid replacing an existing signing identity.')
    folder = Path.home() / 'KlipSigning'
    folder.mkdir(mode=0o700, exist_ok=True)
    key = folder / 'klip-release.p12'
    if key.exists():
        sys.exit(f'{key} already exists. Stopping to avoid replacing your signing identity. See docs/RELEASING.md for recovery.')
    print('Create a strong signing password and save it in a password manager.')
    password = getpass.getpass('New signing password: ')
    if len(password) < 12 or password != getpass.getpass('Repeat password: '):
        sys.exit('Passwords must match and contain at least 12 characters.')
    env = dict(os.environ, KLIP_SETUP_PASSWORD=password)
    try:
        subprocess.run(['keytool', '-genkeypair', '-keystore', str(key), '-storetype', 'PKCS12',
                        '-alias', ALIAS, '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10000',
                        '-dname', 'CN=Klip', '-storepass:env', 'KLIP_SETUP_PASSWORD',
                        '-keypass:env', 'KLIP_SETUP_PASSWORD', '-noprompt'], env=env, check=True)
        if os.name != 'nt':
            key.chmod(0o600)
        print(f'Private signing key created at {key}. Back it up securely before continuing.')
        if input('Type BACKED UP after you have backed it up: ').strip() != 'BACKED UP':
            sys.exit('Stopped. Your key remains at the path above; see docs/RELEASING.md for recovery.')
        encoded = base64.b64encode(key.read_bytes())
        if len(encoded) > 48 * 1024:
            sys.exit('Keystore is too large for a GitHub Actions secret.')
        for name, value in [('KLIP_KEYSTORE_BASE64', encoded),
                            ('KLIP_KEYSTORE_PASSWORD', password.encode()),
                            ('KLIP_KEY_ALIAS', ALIAS.encode()),
                            ('KLIP_KEY_PASSWORD', password.encode())]:
            subprocess.run(['gh', 'secret', 'set', name, '--repo', REPO], input=value, check=True)
        print('Signing secrets were sent to the Klip GitHub repository. Never commit or share your key or password.')
    finally:
        env.pop('KLIP_SETUP_PASSWORD', None)


if __name__ == '__main__':
    try:
        run()
    except (subprocess.CalledProcessError, KeyboardInterrupt) as error:
        sys.exit(f'Setup stopped: {error}. Keep your existing keystore and see docs/RELEASING.md.')
