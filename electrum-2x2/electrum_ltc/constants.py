# -*- coding: utf-8 -*-
#
# Electrum - lightweight Bitcoin client
# Copyright (C) 2018 The Electrum developers
#
# Permission is hereby granted, free of charge, to any person
# obtaining a copy of this software and associated documentation files
# (the "Software"), to deal in the Software without restriction,
# including without limitation the rights to use, copy, modify, merge,
# publish, distribute, sublicense, and/or sell copies of the Software,
# and to permit persons to whom the Software is furnished to do so,
# subject to the following conditions:
#
# The above copyright notice and this permission notice shall be
# included in all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
# EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
# MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
# NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
# BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
# ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
# CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

import os
import json

from .util import inv_dict, all_subclasses
from . import bitcoin


def read_json(filename, default):
    path = os.path.join(os.path.dirname(__file__), filename)
    try:
        with open(path, 'r') as f:
            r = json.loads(f.read())
    except:
        r = default
    return r


GIT_REPO_URL = "https://github.com/pooler/electrum-ltc"
GIT_REPO_ISSUES_URL = "https://github.com/pooler/electrum-ltc/issues"
BIP39_WALLET_FORMATS = read_json('bip39_wallet_formats.json', [])


class AbstractNet:

    NET_NAME: str
    TESTNET: bool
    WIF_PREFIX: int
    ADDRTYPE_P2PKH: int
    ADDRTYPE_P2SH: int
    SEGWIT_HRP: str
    BOLT11_HRP: str
    GENESIS: str
    BLOCK_HEIGHT_FIRST_LIGHTNING_CHANNELS: int = 0
    BIP44_COIN_TYPE: int
    LN_REALM_BYTE: int
    NLAST_POW_BLOCK: int = 0  # 2x2: last PoW block; blocks above this are PoS (no scrypt target)

    @classmethod
    def max_checkpoint(cls) -> int:
        return max(0, len(cls.CHECKPOINTS) * 2016 - 1)

    @classmethod
    def rev_genesis_bytes(cls) -> bytes:
        return bytes.fromhex(bitcoin.rev_hex(cls.GENESIS))


class BitcoinMainnet(AbstractNet):
    # ---- 2x2coin (2X2) mainnet ----
    # Values are authoritative: taken from 2x2Coin/src/chainparams.cpp.
    # 2x2 is a scrypt hybrid PoW->PoS chain (nLastPOWBlock=110000), NO segwit/bech32.

    NET_NAME = "mainnet"
    TESTNET = False
    WIF_PREFIX = 128          # chainparams SECRET_KEY = 128 (0x80)
    ADDRTYPE_P2PKH = 3        # chainparams PUBKEY_ADDRESS = 3
    ADDRTYPE_P2SH = 90        # chainparams SCRIPT_ADDRESS = 90
    SEGWIT_HRP = "x2"         # dummy; 2x2 has no segwit, legacy-only wallets are forced
    BOLT11_HRP = SEGWIT_HRP
    GENESIS = "00000eea834a06692bc4f56d6f0061631c72fd75431ce9e5d7f3b9d712dc3a9b"
    DEFAULT_PORTS = {'t': '50011', 's': '50012'}
    DEFAULT_SERVERS = read_json('servers.json', {})
    CHECKPOINTS = read_json('checkpoints.json', [])
    BLOCK_HEIGHT_FIRST_LIGHTNING_CHANNELS = 0

    XPRV_HEADERS = {
        'standard':    0x0488ade4,  # xprv  (chainparams EXT_SECRET_KEY 0x0488ADE4)
    }
    XPRV_HEADERS_INV = inv_dict(XPRV_HEADERS)
    XPUB_HEADERS = {
        'standard':    0x0488b21e,  # xpub  (chainparams EXT_PUBLIC_KEY 0x0488B21E)
    }
    XPUB_HEADERS_INV = inv_dict(XPUB_HEADERS)
    BIP44_COIN_TYPE = 0        # TODO confirm SLIP-44 index with client (affects restore compat)
    NLAST_POW_BLOCK = 110000   # chainparams consensus.nLastPOWBlock
    LN_REALM_BYTE = 0
    LN_DNS_SEEDS = []          # no lightning on 2x2


class BitcoinTestnet(AbstractNet):
    # ---- 2x2coin (2X2) testnet ---- (chainparams.cpp testnet section)

    NET_NAME = "testnet"
    TESTNET = True
    WIF_PREFIX = 239          # testnet SECRET_KEY = 239
    ADDRTYPE_P2PKH = 51       # testnet PUBKEY_ADDRESS = 51
    ADDRTYPE_P2SH = 50        # testnet SCRIPT_ADDRESS = 50
    SEGWIT_HRP = "tx2"        # dummy; no segwit
    BOLT11_HRP = SEGWIT_HRP
    GENESIS = ""              # testnet genesis assertion is commented out in chainparams; fill if needed
    DEFAULT_PORTS = {'t': '51011', 's': '51012'}
    DEFAULT_SERVERS = read_json('servers_testnet.json', {})
    CHECKPOINTS = read_json('checkpoints_testnet.json', [])

    XPRV_HEADERS = {
        'standard':    0x04358394,  # tprv  (testnet EXT_SECRET_KEY 0x04358394)
    }
    XPRV_HEADERS_INV = inv_dict(XPRV_HEADERS)
    XPUB_HEADERS = {
        'standard':    0x043587cf,  # tpub  (testnet EXT_PUBLIC_KEY 0x043587CF)
    }
    XPUB_HEADERS_INV = inv_dict(XPUB_HEADERS)
    BIP44_COIN_TYPE = 1
    LN_REALM_BYTE = 1
    LN_DNS_SEEDS = [  # TODO investigate this again
        #'test.nodes.lightning.directory.',  # times out.
        #'lseed.bitcoinstats.com.',  # ignores REALM byte and returns mainnet peers...
    ]


class BitcoinRegtest(BitcoinTestnet):

    NET_NAME = "regtest"
    SEGWIT_HRP = "rltc"
    BOLT11_HRP = SEGWIT_HRP
    GENESIS = "530827f38f93b43ed12af0b3ad25a288dc02ed74d6d7857862df51fc56c416f9"
    DEFAULT_SERVERS = read_json('servers_regtest.json', {})
    CHECKPOINTS = []
    LN_DNS_SEEDS = []


class BitcoinSimnet(BitcoinTestnet):

    NET_NAME = "simnet"
    WIF_PREFIX = 0x64
    ADDRTYPE_P2PKH = 0x3f
    ADDRTYPE_P2SH = 0x7b
    SEGWIT_HRP = "sb"
    BOLT11_HRP = SEGWIT_HRP
    GENESIS = "683e86bd5c6d110d91b94b97137ba6bfe02dbbdb8e3dff722a669b5d69d77af6"
    DEFAULT_SERVERS = read_json('servers_regtest.json', {})
    CHECKPOINTS = []
    LN_DNS_SEEDS = []


class BitcoinSignet(BitcoinTestnet):

    NET_NAME = "signet"
    BOLT11_HRP = "tbs"
    GENESIS = "00000008819873e925422c1ff0f99f7cc9bbb232af63a077a480a3633bee1ef6"
    DEFAULT_SERVERS = read_json('servers_signet.json', {})
    CHECKPOINTS = []
    LN_DNS_SEEDS = []


NETS_LIST = tuple(all_subclasses(AbstractNet))

# don't import net directly, import the module instead (so that net is singleton)
net = BitcoinMainnet

def set_signet():
    global net
    net = BitcoinSignet

def set_simnet():
    global net
    net = BitcoinSimnet

def set_mainnet():
    global net
    net = BitcoinMainnet

def set_testnet():
    global net
    net = BitcoinTestnet

def set_regtest():
    global net
    net = BitcoinRegtest
