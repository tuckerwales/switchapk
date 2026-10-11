#!/usr/bin/env python3
"""Generates the TLS test PKI in tests/tls (committed; rerun only to change it, then update TlsTest's CA).

  root.pem          test root CA (RSA 2048), trusted by tests/dex/TlsTest.java
  inter.pem         intermediate CA (EC P-256) under the root
  good.pem/.key     leaf for localhost and 127.0.0.1 (RSA 2048, serverAuth), chain: leaf + intermediate
  expired.pem/.key  the same names, valid only in 2001
  wronghost.pem/.key  leaf for other.example
  selfsigned.pem/.key  self-signed localhost certificate nobody trusts

Needs the Python cryptography package. Keys are test-only and protect nothing.
"""
import datetime
import ipaddress
import os

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec, rsa
from cryptography.x509.oid import ExtendedKeyUsageOID, NameOID

HERE = os.path.dirname(os.path.abspath(__file__))
NOW = datetime.datetime(2026, 10, 11, tzinfo=datetime.timezone.utc)
LONG = (NOW - datetime.timedelta(days=1), NOW + datetime.timedelta(days=365 * 60))
PAST = (datetime.datetime(2001, 1, 1, tzinfo=datetime.timezone.utc),
        datetime.datetime(2002, 1, 1, tzinfo=datetime.timezone.utc))


def name(cn):
    return x509.Name([x509.NameAttribute(NameOID.COUNTRY_NAME, "GB"),
                      x509.NameAttribute(NameOID.ORGANIZATION_NAME, "switchapk tests"),
                      x509.NameAttribute(NameOID.COMMON_NAME, cn)])


def cert(subject, key, issuer, issuer_key, serial, validity, ca, sans=None):
    b = (x509.CertificateBuilder().subject_name(name(subject)).issuer_name(name(issuer))
         .public_key(key.public_key()).serial_number(serial)
         .not_valid_before(validity[0]).not_valid_after(validity[1])
         .add_extension(x509.BasicConstraints(ca=ca, path_length=None), critical=True)
         .add_extension(x509.SubjectKeyIdentifier.from_public_key(key.public_key()), critical=False))
    if ca:
        b = b.add_extension(x509.KeyUsage(digital_signature=True, content_commitment=False, key_encipherment=False,
                                          data_encipherment=False, key_agreement=False, key_cert_sign=True,
                                          crl_sign=True, encipher_only=False, decipher_only=False), critical=True)
    else:
        b = b.add_extension(x509.KeyUsage(digital_signature=True, content_commitment=False, key_encipherment=True,
                                          data_encipherment=False, key_agreement=False, key_cert_sign=False,
                                          crl_sign=False, encipher_only=False, decipher_only=False), critical=True)
        b = b.add_extension(x509.ExtendedKeyUsage([ExtendedKeyUsageOID.SERVER_AUTH]), critical=False)
    if sans:
        b = b.add_extension(x509.SubjectAlternativeName(sans), critical=False)
    return b.sign(issuer_key, hashes.SHA256())


def write(fname, *items):
    with open(os.path.join(HERE, fname), "wb") as f:
        for it in items:
            if isinstance(it, x509.Certificate):
                f.write(it.public_bytes(serialization.Encoding.PEM))
            else:
                f.write(it.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                                         serialization.NoEncryption()))


def main():
    local = [x509.DNSName("localhost"), x509.IPAddress(ipaddress.ip_address("127.0.0.1"))]
    root_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    root = cert("switchapk test root", root_key, "switchapk test root", root_key, 1, LONG, True)
    inter_key = ec.generate_private_key(ec.SECP256R1())
    inter = cert("switchapk test intermediate", inter_key, "switchapk test root", root_key, 2, LONG, True)
    write("root.pem", root)
    write("inter.pem", inter)
    for fname, cn, validity, sans, serial in (("good", "localhost", LONG, local, 3),
                                              ("expired", "localhost", PAST, local, 4),
                                              ("wronghost", "other.example", LONG,
                                               [x509.DNSName("other.example")], 5)):
        k = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        write(fname + ".pem", cert(cn, k, "switchapk test intermediate", inter_key, serial, validity, False, sans),
              inter)
        write(fname + ".key", k)
    k = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    write("selfsigned.pem", cert("localhost", k, "localhost", k, 6, LONG, False, local))
    write("selfsigned.key", k)


if __name__ == "__main__":
    main()
