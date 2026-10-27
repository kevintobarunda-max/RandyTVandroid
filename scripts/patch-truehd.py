#!/usr/bin/env python3
"""Media3 1.4.1 JNI fix for stereo TrueHD initialization/reset (androidx/media#1843)."""
from pathlib import Path
path = Path('decoder-ffmpeg/upstream/src/main/jni/ffmpeg_jni.cc')
source = path.read_text()
if '// HY300: retain TrueHD layout across flushes.' not in source:
    start = source.index('  AVCodecID codecId = context->codec_id;')
    end = source.index('  avcodec_flush_buffers(context);', start)
    source = source[:start] + '  // HY300: retain TrueHD layout across flushes.\n' + source[end:]
    old = '''  if (context->codec_id == AV_CODEC_ID_PCM_MULAW ||
      context->codec_id == AV_CODEC_ID_PCM_ALAW) {
    context->sample_rate = rawSampleRate;
    av_channel_layout_default(&context->ch_layout, rawChannelCount);
  }'''
    new = '''  if (context->codec_id == AV_CODEC_ID_PCM_MULAW ||
      context->codec_id == AV_CODEC_ID_PCM_ALAW ||
      context->codec_id == AV_CODEC_ID_TRUEHD) {
    if (rawSampleRate > 0) context->sample_rate = rawSampleRate;
    if (rawChannelCount > 0) {
      av_channel_layout_default(&context->ch_layout, rawChannelCount);
    }
  }'''
    assert old in source, 'Unexpected Media3 JNI source; inspect before patching'
    source = source.replace(old, new)
    path.write_text(source)
