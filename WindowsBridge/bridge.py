#!/usr/bin/env python3
"""Freelander Control USB-to-MIDI bridge for Windows.

Receives short MIDI messages over an ADB-forwarded TCP socket and sends them
through a Windows MIDI output port (for example, a loopMIDI virtual port).
Uses only Python's standard library and the Windows WinMM API.
"""

from __future__ import annotations

import argparse
import ctypes
import socket
import sys
import time
from ctypes import wintypes

TCP_HOST = "127.0.0.1"
TCP_PORT = 8765
MIDIERR_NOERROR = 0
CALLBACK_NULL = 0


class MIDIOUTCAPSW(ctypes.Structure):
    _fields_ = [
        ("wMid", wintypes.WORD),
        ("wPid", wintypes.WORD),
        ("vDriverVersion", wintypes.DWORD),
        ("szPname", wintypes.WCHAR * 32),
        ("wTechnology", wintypes.WORD),
        ("wVoices", wintypes.WORD),
        ("wNotes", wintypes.WORD),
        ("wChannelMask", wintypes.WORD),
        ("dwSupport", wintypes.DWORD),
    ]


class WinMMMidiOut:
    def __init__(self, wanted_name: str):
        if sys.platform != "win32":
            raise RuntimeError("This bridge is intended for Windows.")
        self.winmm = ctypes.WinDLL("winmm", use_last_error=True)
        self.handle = ctypes.c_void_p()
        self.device_name = ""
        self._declare_api()
        self._open_device(wanted_name)

    def _declare_api(self):
        self.winmm.midiOutGetNumDevs.restype = wintypes.UINT
        self.winmm.midiOutGetDevCapsW.argtypes = [
            ctypes.c_size_t, ctypes.POINTER(MIDIOUTCAPSW), wintypes.UINT
        ]
        self.winmm.midiOutGetDevCapsW.restype = wintypes.UINT
        self.winmm.midiOutOpen.argtypes = [
            ctypes.POINTER(ctypes.c_void_p), wintypes.UINT,
            ctypes.c_size_t, ctypes.c_size_t, wintypes.DWORD
        ]
        self.winmm.midiOutOpen.restype = wintypes.UINT
        self.winmm.midiOutShortMsg.argtypes = [ctypes.c_void_p, wintypes.DWORD]
        self.winmm.midiOutShortMsg.restype = wintypes.UINT
        self.winmm.midiOutReset.argtypes = [ctypes.c_void_p]
        self.winmm.midiOutReset.restype = wintypes.UINT
        self.winmm.midiOutClose.argtypes = [ctypes.c_void_p]
        self.winmm.midiOutClose.restype = wintypes.UINT

    def _open_device(self, wanted_name: str):
        count = int(self.winmm.midiOutGetNumDevs())
        devices = []
        wanted = wanted_name.casefold()
        selected = None
        for index in range(count):
            caps = MIDIOUTCAPSW()
            result = self.winmm.midiOutGetDevCapsW(
                index, ctypes.byref(caps), ctypes.sizeof(caps)
            )
            if result == MIDIERR_NOERROR:
                name = str(caps.szPname)
                devices.append((index, name))
                if name.casefold() == wanted:
                    selected = (index, name)
        if selected is None:
            names = "\n".join(f"  {i}: {name}" for i, name in devices) or "  (no MIDI outputs found)"
            raise RuntimeError(
                f'MIDI output port "{wanted_name}" not found. Available outputs:\n{names}\n'
                "Create the port in loopMIDI and keep loopMIDI running."
            )
        index, self.device_name = selected
        result = self.winmm.midiOutOpen(
            ctypes.byref(self.handle), index, 0, 0, CALLBACK_NULL
        )
        if result != MIDIERR_NOERROR:
            raise RuntimeError(f"midiOutOpen failed with WinMM code {result}.")

    def send(self, status: int, data1: int, data2: int):
        for value, label in ((status, "status"), (data1, "data1"), (data2, "data2")):
            if not 0 <= value <= 255:
                raise ValueError(f"{label} must be 0..255")
        if status < 0x80 or status > 0xEF:
            raise ValueError(f"Unsupported MIDI status byte: {status}")
        if data1 > 127 or data2 > 127:
            raise ValueError("MIDI data bytes must be 0..127")
        packed = status | (data1 << 8) | (data2 << 16)
        result = self.winmm.midiOutShortMsg(self.handle, packed)
        if result != MIDIERR_NOERROR:
            raise RuntimeError(f"midiOutShortMsg failed with WinMM code {result}.")

    def close(self):
        if self.handle:
            self.winmm.midiOutReset(self.handle)
            self.winmm.midiOutClose(self.handle)
            self.handle = ctypes.c_void_p()


def release_all_notes(midi_out: WinMMMidiOut):
    # Reset the port so a dropped USB connection cannot leave hanging notes.
    try:
        midi_out.winmm.midiOutReset(midi_out.handle)
    except Exception:
        pass


def parse_midi_line(line: bytes):
    text = line.decode("ascii", errors="strict").strip()
    if not text:
        return None
    parts = text.split(",")
    if len(parts) != 3:
        raise ValueError(f"Expected status,data1,data2; got: {text!r}")
    status, data1, data2 = (int(part.strip(), 10) for part in parts)
    if not (0x80 <= status <= 0xEF and 0 <= data1 <= 127 and 0 <= data2 <= 127):
        raise ValueError(f"Invalid MIDI message: {text!r}")
    return status, data1, data2


def main():
    parser = argparse.ArgumentParser(description="Freelander PD500C ADB-to-MIDI bridge")
    parser.add_argument("--host", default=TCP_HOST)
    parser.add_argument("--tcp-port", type=int, default=TCP_PORT)
    parser.add_argument("--port-name", default="Freelander Control")
    parser.add_argument("--retry-seconds", type=float, default=2.0)
    args = parser.parse_args()

    midi_out = WinMMMidiOut(args.port_name)
    print(f'MIDI output opened: "{midi_out.device_name}"')
    print(f"Waiting for tablet on {args.host}:{args.tcp_port} (ADB forward)...")
    try:
        while True:
            try:
                with socket.create_connection((args.host, args.tcp_port), timeout=3.0) as sock:
                    sock.settimeout(None)
                    print("Tablet connected over USB/ADB.")
                    buffer = b""
                    while True:
                        chunk = sock.recv(1024)
                        if not chunk:
                            raise ConnectionError("Tablet socket closed")
                        buffer += chunk
                        while b"\n" in buffer:
                            line, buffer = buffer.split(b"\n", 1)
                            message = parse_midi_line(line)
                            if message is not None:
                                midi_out.send(*message)
            except (OSError, ConnectionError, ValueError) as exc:
                release_all_notes(midi_out)
                print(f"Connection/message issue: {exc}. Retrying in {args.retry_seconds:g}s...")
                time.sleep(max(0.2, args.retry_seconds))
    except KeyboardInterrupt:
        print("Stopping bridge...")
    finally:
        midi_out.close()


if __name__ == "__main__":
    main()
