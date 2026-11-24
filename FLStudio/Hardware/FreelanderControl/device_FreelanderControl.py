# name=Freelander Control
# Freelander PD500C MIDI controller script for FL Studio 26.x
# MIDI notes are deliberately left unhandled so FL Studio's generic note
# processing can play the selected Channel Rack instrument / FPC.

import mixer
import playlist
import transport


def OnInit():
    print('Freelander Control: init ok')


def OnMidiMsg(eventData):
    status = eventData.status & 0xF0
    if status != 0xB0:  # only handle Control Change; let notes pass through
        return

    cc = eventData.data1
    value = eventData.data2

    # Mixer volume: CC 20-27 -> Mixer tracks 1-8 (0 is Master)
    if 20 <= cc <= 27:
        track = cc - 19
        mixer.setTrackVolume(track, value / 127.0)
        eventData.handled = True
        return

    # Mixer pan: CC 28-35 -> Mixer tracks 1-8, -1.0..+1.0
    if 28 <= cc <= 35:
        track = cc - 27
        mixer.setTrackPan(track, (value / 127.0) * 2.0 - 1.0)
        eventData.handled = True
        return

    # Mixer mute: CC 40-47. Trigger only on touch-down, not touch-up.
    if 40 <= cc <= 47:
        if value > 0:
            mixer.muteTrack(cc - 39)
        eventData.handled = True
        return

    # Mixer solo: CC 48-55.
    if 48 <= cc <= 55:
        if value > 0:
            mixer.soloTrack(cc - 47)
        eventData.handled = True
        return

    # Playlist track mute: CC 60-67 (Playlist indices 0-7).
    if 60 <= cc <= 67:
        if value > 0:
            playlist.muteTrack(cc - 60)
        eventData.handled = True
        return

    # Playlist track solo: CC 68-75.
    if 68 <= cc <= 75:
        if value > 0:
            playlist.soloTrack(cc - 68)
        eventData.handled = True
        return

    # Playlist track select: CC 76-83. Select only one track at a time.
    if 76 <= cc <= 83:
        if value > 0:
            playlist.deselectAll()
            playlist.selectTrack(cc - 76)
        eventData.handled = True
        return

    # Transport buttons: CC 90=Play, 91=Stop, 92=Record.
    if cc == 90:
        if value > 0:
            transport.start()
        eventData.handled = True
        return
    if cc == 91:
        if value > 0:
            transport.stop()
        eventData.handled = True
        return
    if cc == 92:
        if value > 0:
            transport.record()
        eventData.handled = True
        return

    # CC 100-106 are reserved for seven EQ controls. They are intentionally
    # not handled here, so FL Studio's normal MIDI Learn/Link to controller
    # can bind them to the seven desired Parametric EQ 2 parameters.
