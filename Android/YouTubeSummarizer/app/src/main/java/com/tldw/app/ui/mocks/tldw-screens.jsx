// tldw-screens.jsx — all 11 screen states for TLDW
// Each screen is a function (t) => JSX where t is the resolved theme token set.

// ─────────────────────────────────────────────────────────────
// Shared atoms
// ─────────────────────────────────────────────────────────────

function TLDWLogo({ t, size = 28 }) {
  // Original wordmark — abstract "knowledge bit" mark + TLDW
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
      <div style={{
        width: size, height: size, borderRadius: size * 0.32,
        background: t.accent,
        display: 'grid', placeItems: 'center',
        boxShadow: `inset 0 -2px 0 rgba(0,0,0,.08)`,
      }}>
        <div style={{
          width: size * 0.42, height: size * 0.42,
          borderRadius: 2,
          background: t.onSurface,
        }} />
      </div>
      <span style={{
        fontFamily: "'Roboto Flex', sans-serif",
        fontWeight: 700, fontSize: size * 0.62,
        letterSpacing: -0.5, color: t.onSurface,
      }}>TLDW</span>
    </div>
  );
}

// Top app bar (M3 small)
function AppBar({ t, title, leading, trailing, subdued = false }) {
  return (
    <div style={{
      height: 64, display: 'flex', alignItems: 'center', gap: 4,
      padding: '0 4px 0 4px', flexShrink: 0,
      background: subdued ? t.surfaceContainer : 'transparent',
    }}>
      <div style={{ width: 48, height: 48, display: 'grid', placeItems: 'center' }}>
        {leading}
      </div>
      <div style={{
        flex: 1, fontSize: 22, fontWeight: 500,
        color: t.onSurface, letterSpacing: 0,
      }}>{title}</div>
      <div style={{ width: 48, height: 48, display: 'grid', placeItems: 'center' }}>
        {trailing}
      </div>
    </div>
  );
}

function IconBtn({ glyph, t, color }) {
  return (
    <button className="m3-btn" style={{
      width: 40, height: 40, borderRadius: 20, border: 'none',
      background: 'transparent', color: color || t.onSurface,
      display: 'grid', placeItems: 'center',
    }}>
      <span className="mi" style={{ fontSize: 22 }}>{glyph}</span>
    </button>
  );
}

// Filled M3 button
function FilledBtn({ children, t, full, disabled, tone = 'primary', icon }) {
  const bg = tone === 'error' ? t.error : t.primary;
  const fg = tone === 'error' ? t.onError : t.onPrimary;
  return (
    <button className="m3-btn" disabled={disabled} style={{
      height: 56, padding: '0 24px', minWidth: 100,
      width: full ? '100%' : undefined,
      borderRadius: 28, border: 'none',
      background: disabled ? t.surfaceContainerHighest : bg,
      color: disabled ? t.outline : fg,
      fontSize: 16, fontWeight: 600, fontFamily: 'inherit',
      letterSpacing: 0.1,
      display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 10,
      cursor: disabled ? 'default' : 'pointer',
    }}>
      {icon && <span className="mi" style={{ fontSize: 20 }}>{icon}</span>}
      {children}
    </button>
  );
}

// Tonal button (for secondary actions)
function TonalBtn({ children, t, full, icon }) {
  return (
    <button className="m3-btn" style={{
      height: 48, padding: '0 20px',
      width: full ? '100%' : undefined,
      borderRadius: 24, border: 'none',
      background: t.surfaceContainerHigh, color: t.onSurface,
      fontSize: 15, fontWeight: 500, fontFamily: 'inherit',
      display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 8,
    }}>
      {icon && <span className="mi" style={{ fontSize: 18 }}>{icon}</span>}
      {children}
    </button>
  );
}

// Outlined "text" button
function TextBtn({ children, t, icon }) {
  return (
    <button className="m3-btn" style={{
      height: 40, padding: '0 16px',
      borderRadius: 20, border: 'none',
      background: 'transparent', color: t.primary,
      fontSize: 14, fontWeight: 600, fontFamily: 'inherit',
      display: 'inline-flex', alignItems: 'center', gap: 8,
    }}>
      {icon && <span className="mi" style={{ fontSize: 18 }}>{icon}</span>}
      {children}
    </button>
  );
}

// Card wrapper
function Card({ t, children, padded = true, style }) {
  return (
    <div style={{
      background: t.surfaceContainer,
      borderRadius: 16,
      padding: padded ? 16 : 0,
      ...style,
    }}>{children}</div>
  );
}

// Linear progress indicator (M3 wavy-ish — we use a simple smooth bar)
function LinearProgress({ t, value, indeterminate }) {
  return (
    <div style={{
      height: 8, width: '100%',
      background: t.surfaceContainerHighest,
      borderRadius: 4, overflow: 'hidden', position: 'relative',
    }}>
      {indeterminate ? (
        <div className="shimmer" style={{
          position: 'absolute', inset: 0, background: t.accent, opacity: 0.9,
        }} />
      ) : (
        <div style={{
          height: '100%', width: `${value}%`,
          background: t.accent,
          borderRadius: 4,
          transition: 'width .3s',
        }} />
      )}
    </div>
  );
}

// Circular progress ring
function CircularProgress({ t, value = 0, size = 160, thick = 12, indeterminate, accent }) {
  const r = (size - thick) / 2;
  const c = 2 * Math.PI * r;
  const off = c - (value / 100) * c;
  return (
    <div style={{ position: 'relative', width: size, height: size }}>
      <svg width={size} height={size} className={indeterminate ? 'spin' : ''}>
        <circle cx={size/2} cy={size/2} r={r}
          stroke={t.surfaceContainerHighest} strokeWidth={thick} fill="none"/>
        <circle cx={size/2} cy={size/2} r={r}
          stroke={accent || t.accent} strokeWidth={thick} fill="none"
          strokeLinecap="round"
          strokeDasharray={c}
          strokeDashoffset={indeterminate ? c * 0.75 : off}
          transform={`rotate(-90 ${size/2} ${size/2})`}
          style={{ transition: 'stroke-dashoffset .3s' }}/>
      </svg>
    </div>
  );
}

// FAB-ish big primary action wrapper
function ScreenPad({ children, style }) {
  return (
    <div style={{
      flex: 1, padding: '0 20px 20px', display: 'flex', flexDirection: 'column',
      ...style,
    }}>{children}</div>
  );
}

// ─────────────────────────────────────────────────────────────
// SCREEN 1 — Initial (before download)
// ─────────────────────────────────────────────────────────────
function ScreenInitial(t) {
  return (
    <>
      <AppBar t={t}
        leading={<IconBtn t={t} glyph="menu" />}
        trailing={<IconBtn t={t} glyph="info" />}
        title="" />
      <ScreenPad>
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center', textAlign: 'center', gap: 28 }}>
          {/* Hero mark */}
          <div style={{ position: 'relative' }}>
            <div style={{
              width: 132, height: 132, borderRadius: 36,
              background: t.primaryContainer,
              display: 'grid', placeItems: 'center',
              boxShadow: `0 12px 28px ${t.scrim}`,
            }}>
              <span className="mi" style={{ fontSize: 64, color: t.onPrimaryContainer }}>auto_awesome</span>
            </div>
            <div style={{
              position: 'absolute', right: -8, bottom: -8,
              width: 44, height: 44, borderRadius: 22,
              background: t.accent,
              display: 'grid', placeItems: 'center',
              boxShadow: `0 4px 10px ${t.scrim}`,
            }}>
              <span className="mi" style={{ fontSize: 22, color: '#1d1c14' }}>cloud_download</span>
            </div>
          </div>

          <div>
            <TLDWLogo t={t} size={36} />
          </div>

          <div style={{ maxWidth: 280 }}>
            <div style={{ fontSize: 28, fontWeight: 500, lineHeight: 1.15, color: t.onSurface, letterSpacing: -0.5 }}>
              Turn long videos into knowledge bits.
            </div>
            <div style={{ marginTop: 14, fontSize: 14, color: t.onSurfaceVar, lineHeight: 1.5 }}>
              Download a small language model once. Summarize on-device, forever — no cloud, no key.
            </div>
          </div>
        </div>

        {/* Model card */}
        <Card t={t} style={{ marginBottom: 16 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <div style={{ width: 40, height: 40, borderRadius: 12, background: t.surfaceContainerHighest, display: 'grid', placeItems: 'center' }}>
              <span className="mi" style={{ fontSize: 20, color: t.onSurfaceVar }}>memory</span>
            </div>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 14, fontWeight: 600 }}>Summarizer model</div>
              <div style={{ fontSize: 12, color: t.onSurfaceVar }}>One-time download · ~1 min on Wi-Fi</div>
            </div>
            <div style={{
              fontFamily: 'Roboto Mono, monospace', fontSize: 12,
              padding: '4px 10px', borderRadius: 100,
              background: t.surfaceContainerHighest, color: t.onSurfaceVar,
            }}>1.2 GB</div>
          </div>
        </Card>

        <FilledBtn t={t} full icon="download">Download model</FilledBtn>
      </ScreenPad>
    </>
  );
}

// ─────────────────────────────────────────────────────────────
// SCREEN 1B — Downloading (with progress)
// ─────────────────────────────────────────────────────────────
function ScreenDownloading(t) {
  const pct = 42;
  return (
    <>
      <AppBar t={t}
        leading={<IconBtn t={t} glyph="close" />}
        trailing={<div />}
        title="" />
      <ScreenPad>
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center', textAlign: 'center', gap: 32 }}>

          <div style={{ position: 'relative' }}>
            <CircularProgress t={t} value={pct} size={180} thick={14} />
            <div style={{
              position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column',
              alignItems: 'center', justifyContent: 'center',
            }}>
              <div style={{ fontSize: 44, fontWeight: 600, color: t.onSurface, fontFamily: "'Roboto Flex', sans-serif", letterSpacing: -1.5, lineHeight: 1 }}>
                {pct}<span style={{ fontSize: 22, color: t.onSurfaceVar, fontWeight: 500 }}>%</span>
              </div>
              <div style={{ fontSize: 11, color: t.onSurfaceVar, marginTop: 4, fontFamily: 'Roboto Mono, monospace', letterSpacing: 0.4 }}>
                504 / 1200 MB
              </div>
            </div>
          </div>

          <div>
            <div style={{ fontSize: 24, fontWeight: 500, color: t.onSurface, letterSpacing: -0.3 }}>
              Downloading model
            </div>
            <div style={{ marginTop: 10, fontSize: 14, color: t.onSurfaceVar, maxWidth: 260, margin: '10px auto 0', lineHeight: 1.5 }}>
              You can keep using your phone. We'll let you know when it's ready.
            </div>
          </div>

          {/* Speed chip */}
          <div style={{
            display: 'inline-flex', alignItems: 'center', gap: 8,
            padding: '8px 14px', borderRadius: 100,
            background: t.surfaceContainer, color: t.onSurfaceVar, fontSize: 12,
            fontFamily: 'Roboto Mono, monospace',
          }}>
            <span className="mi" style={{ fontSize: 14, color: t.accent }}>bolt</span>
            8.4 MB/s · ~1m 22s left
          </div>
        </div>

        <TonalBtn t={t} full icon="pause">Pause download</TonalBtn>
      </ScreenPad>
    </>
  );
}

// ─────────────────────────────────────────────────────────────
// SCREEN 1C — Download failed
// ─────────────────────────────────────────────────────────────
function ScreenDownloadFailed(t) {
  return (
    <>
      <AppBar t={t}
        leading={<IconBtn t={t} glyph="close" />}
        trailing={<div />}
        title="" />
      <ScreenPad>
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center', textAlign: 'center', gap: 24 }}>
          <div style={{
            width: 96, height: 96, borderRadius: 28,
            background: t.errorContainer, color: t.onErrorContainer,
            display: 'grid', placeItems: 'center',
          }}>
            <span className="mi" style={{ fontSize: 48 }}>error_outline</span>
          </div>

          <div style={{ maxWidth: 290 }}>
            <div style={{ fontSize: 24, fontWeight: 500, color: t.onSurface, letterSpacing: -0.3 }}>
              Download interrupted
            </div>
            <div style={{ marginTop: 12, fontSize: 14, color: t.onSurfaceVar, lineHeight: 1.55 }}>
              The connection dropped at 504 MB. We saved what we have — try again to resume.
            </div>
          </div>

          {/* Detail card */}
          <Card t={t} style={{ width: '100%', textAlign: 'left' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <span className="mi" style={{ fontSize: 20, color: t.onSurfaceVar }}>error</span>
              <div style={{ flex: 1 }}>
                <div style={{ fontFamily: 'Roboto Mono, monospace', fontSize: 12, color: t.onSurfaceVar }}>NETWORK_TIMEOUT</div>
                <div style={{ fontSize: 13, color: t.onSurface, marginTop: 2 }}>Server stopped responding</div>
              </div>
            </div>
          </Card>
        </div>

        <div style={{ display: 'flex', gap: 12 }}>
          <TonalBtn t={t} icon="bug_report">Details</TonalBtn>
          <div style={{ flex: 1 }}>
            <FilledBtn t={t} full icon="refresh">Resume</FilledBtn>
          </div>
        </div>
      </ScreenPad>
    </>
  );
}

// ─────────────────────────────────────────────────────────────
// SCREEN 1D — No internet
// ─────────────────────────────────────────────────────────────
function ScreenNoInternet(t) {
  return (
    <>
      <AppBar t={t}
        leading={<IconBtn t={t} glyph="arrow_back" />}
        trailing={<div />}
        title="" />
      <ScreenPad>
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center', textAlign: 'center', gap: 24 }}>
          <div style={{
            width: 110, height: 110, borderRadius: 32,
            background: t.surfaceContainer, color: t.onSurfaceVar,
            display: 'grid', placeItems: 'center', position: 'relative',
          }}>
            <span className="mi" style={{ fontSize: 56 }}>signal_wifi_off</span>
          </div>

          <div style={{ maxWidth: 290 }}>
            <div style={{ fontSize: 26, fontWeight: 500, color: t.onSurface, letterSpacing: -0.3 }}>
              You're offline
            </div>
            <div style={{ marginTop: 12, fontSize: 14, color: t.onSurfaceVar, lineHeight: 1.55 }}>
              The model needs Wi-Fi or mobile data to download. Once it's on your device, TLDW works fully offline.
            </div>
          </div>

          {/* Inline checklist */}
          <div style={{ width: '100%', textAlign: 'left' }}>
            <Card t={t} padded={false}>
              {[
                { ok: false, glyph: 'wifi', label: 'Wi-Fi', sub: 'Not connected' },
                { ok: false, glyph: 'signal_cellular_alt', label: 'Mobile data', sub: 'Off' },
                { ok: true, glyph: 'airplanemode_active', label: 'Airplane mode', sub: 'On' },
              ].map((row, i, arr) => (
                <div key={i} style={{
                  display: 'flex', alignItems: 'center', gap: 14, padding: '12px 16px',
                  borderBottom: i < arr.length - 1 ? `1px solid ${t.outlineVar}` : 'none',
                }}>
                  <span className="mi" style={{ fontSize: 22, color: row.ok ? t.error : t.onSurfaceVar }}>{row.glyph}</span>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontSize: 14, color: t.onSurface, fontWeight: 500 }}>{row.label}</div>
                    <div style={{ fontSize: 12, color: t.onSurfaceVar }}>{row.sub}</div>
                  </div>
                  <span className="mi" style={{
                    fontSize: 18, color: row.ok ? t.error : t.outline,
                  }}>{row.ok ? 'warning' : 'close'}</span>
                </div>
              ))}
            </Card>
          </div>
        </div>

        <FilledBtn t={t} full icon="settings">Open network settings</FilledBtn>
      </ScreenPad>
    </>
  );
}

// ─────────────────────────────────────────────────────────────
// SCREEN 2 — Empty (paste URL)
// ─────────────────────────────────────────────────────────────
function ScreenEmpty(t) {
  return (
    <>
      <AppBar t={t}
        leading={<TLDWLogo t={t} size={26} />}
        trailing={<IconBtn t={t} glyph="more_vert" />}
        title="" />
      <ScreenPad>
        <div style={{ marginTop: 8 }}>
          <div style={{ fontSize: 32, fontWeight: 500, lineHeight: 1.1, color: t.onSurface, letterSpacing: -0.8 }}>
            Paste a YouTube link
          </div>
          <div style={{ marginTop: 10, fontSize: 14, color: t.onSurfaceVar, lineHeight: 1.5 }}>
            We'll grab the transcript and summarize it on-device in a few seconds.
          </div>
        </div>

        {/* Input field */}
        <div style={{ marginTop: 24 }}>
          <div style={{
            border: `1px solid ${t.outlineVar}`,
            borderRadius: 16, padding: '14px 14px 14px 52px',
            background: t.surface, position: 'relative',
            minHeight: 64, display: 'flex', alignItems: 'center',
          }}>
            <span className="mi" style={{
              position: 'absolute', left: 16, top: '50%', transform: 'translateY(-50%)',
              fontSize: 22, color: t.onSurfaceVar,
            }}>link</span>
            <span style={{ color: t.outline, fontSize: 15 }}>youtube.com/watch?v=…</span>
          </div>
          <div style={{ marginTop: 10, display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0 4px' }}>
            <div style={{ fontSize: 12, color: t.onSurfaceVar, display: 'flex', alignItems: 'center', gap: 6 }}>
              <span className="mi" style={{ fontSize: 14, color: t.success }}>check_circle</span>
              Model ready · runs on-device
            </div>
            <TextBtn t={t} icon="content_paste">Paste</TextBtn>
          </div>
        </div>

        {/* Examples */}
        <div style={{ marginTop: 28 }}>
          <div style={{
            fontSize: 11, fontWeight: 600, color: t.onSurfaceVar,
            textTransform: 'uppercase', letterSpacing: 1.4, marginBottom: 10, padding: '0 4px',
          }}>Try one of these</div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {[
              { dur: '47:12', title: 'How to read a paper, fast', chan: 'Andrej Karpathy' },
              { dur: '1:24:08', title: 'A history of the printing press', chan: 'In Our Time' },
              { dur: '22:40', title: 'Why sleep is your superpower', chan: 'TED' },
            ].map((row, i) => (
              <div key={i} style={{
                display: 'flex', alignItems: 'center', gap: 12,
                padding: '12px 14px', borderRadius: 14,
                background: t.surfaceContainer,
              }}>
                <div style={{
                  width: 56, height: 40, borderRadius: 8, flexShrink: 0,
                  background: t.surfaceContainerHighest, color: t.onSurfaceVar,
                  display: 'grid', placeItems: 'center', position: 'relative',
                }}>
                  <span className="mi" style={{ fontSize: 22 }}>play_arrow</span>
                </div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{
                    fontSize: 14, fontWeight: 500, color: t.onSurface,
                    overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                  }}>{row.title}</div>
                  <div style={{ fontSize: 12, color: t.onSurfaceVar, marginTop: 2 }}>
                    {row.chan} · {row.dur}
                  </div>
                </div>
                <span className="mi" style={{ fontSize: 20, color: t.outline }}>chevron_right</span>
              </div>
            ))}
          </div>
        </div>

        <div style={{ flex: 1 }} />
      </ScreenPad>
    </>
  );
}

// ─────────────────────────────────────────────────────────────
// SCREEN 2B — Fetching transcript
// ─────────────────────────────────────────────────────────────
function ScreenFetching(t) {
  return (
    <>
      <AppBar t={t}
        leading={<IconBtn t={t} glyph="arrow_back" />}
        trailing={<IconBtn t={t} glyph="close" />}
        title="" />
      <ScreenPad>
        {/* Video preview card */}
        <Card t={t} padded={false} style={{ overflow: 'hidden' }}>
          <div style={{
            height: 160, background: t.surfaceContainerHighest,
            position: 'relative',
            backgroundImage: `repeating-linear-gradient(135deg, ${t.outlineVar} 0 1px, transparent 1px 12px)`,
          }}>
            <div style={{
              position: 'absolute', inset: 0,
              display: 'grid', placeItems: 'center',
            }}>
              <div style={{
                width: 56, height: 56, borderRadius: 28,
                background: 'rgba(0,0,0,.5)', color: '#fff',
                display: 'grid', placeItems: 'center',
              }}>
                <span className="mi" style={{ fontSize: 28 }}>play_arrow</span>
              </div>
            </div>
            <div style={{
              position: 'absolute', bottom: 8, right: 8,
              padding: '3px 8px', borderRadius: 4,
              background: 'rgba(0,0,0,.7)', color: '#fff',
              fontSize: 11, fontFamily: 'Roboto Mono, monospace',
            }}>47:12</div>
          </div>
          <div style={{ padding: 14 }}>
            <div style={{ fontSize: 15, fontWeight: 500, color: t.onSurface, lineHeight: 1.3 }}>
              How to read a paper, fast
            </div>
            <div style={{ fontSize: 12, color: t.onSurfaceVar, marginTop: 4 }}>Andrej Karpathy · 412K views</div>
          </div>
        </Card>

        {/* Stepper */}
        <div style={{ marginTop: 24, display: 'flex', flexDirection: 'column', gap: 16 }}>
          {[
            { state: 'done',     label: 'Found video',          sub: '47 min · English captions' },
            { state: 'active',   label: 'Fetching transcript',  sub: 'Pulling captions…' },
            { state: 'pending',  label: 'Generating TL;DR',     sub: 'Runs on your device' },
          ].map((s, i) => (
            <div key={i} style={{ display: 'flex', alignItems: 'flex-start', gap: 14 }}>
              <div style={{ width: 28, display: 'grid', placeItems: 'center', paddingTop: 2 }}>
                {s.state === 'done' && (
                  <div style={{ width: 24, height: 24, borderRadius: 12, background: t.success, display: 'grid', placeItems: 'center' }}>
                    <span className="mi" style={{ fontSize: 16, color: t.onSurface === '#1d1c14' ? '#fff' : t.onSuccessContainer }}>check</span>
                  </div>
                )}
                {s.state === 'active' && (
                  <div className="spin" style={{ width: 24, height: 24 }}>
                    <CircularProgress t={t} indeterminate size={24} thick={3} />
                  </div>
                )}
                {s.state === 'pending' && (
                  <div style={{ width: 24, height: 24, borderRadius: 12, border: `2px solid ${t.outlineVar}` }} />
                )}
              </div>
              <div style={{ flex: 1 }}>
                <div style={{
                  fontSize: 15, fontWeight: s.state === 'active' ? 600 : 500,
                  color: s.state === 'pending' ? t.outline : t.onSurface,
                }}>{s.label}</div>
                <div style={{ fontSize: 12, color: t.onSurfaceVar, marginTop: 2 }}>{s.sub}</div>
              </div>
            </div>
          ))}
        </div>

        <div style={{ flex: 1 }} />
        <LinearProgress t={t} indeterminate />
      </ScreenPad>
    </>
  );
}

// ─────────────────────────────────────────────────────────────
// SCREEN 2C — Generating TL;DR
// ─────────────────────────────────────────────────────────────
function ScreenGenerating(t) {
  return (
    <>
      <AppBar t={t}
        leading={<IconBtn t={t} glyph="arrow_back" />}
        trailing={<IconBtn t={t} glyph="close" />}
        title="" />
      <ScreenPad>
        <Card t={t} padded={false} style={{ overflow: 'hidden' }}>
          <div style={{
            height: 100, background: t.surfaceContainerHighest, position: 'relative',
            backgroundImage: `repeating-linear-gradient(135deg, ${t.outlineVar} 0 1px, transparent 1px 12px)`,
          }}>
            <div style={{ position: 'absolute', bottom: 8, right: 8, padding: '3px 8px', borderRadius: 4, background: 'rgba(0,0,0,.7)', color: '#fff', fontSize: 11, fontFamily: 'Roboto Mono, monospace' }}>47:12</div>
          </div>
          <div style={{ padding: 12 }}>
            <div style={{ fontSize: 13, fontWeight: 500 }}>How to read a paper, fast</div>
          </div>
        </Card>

        <div style={{ marginTop: 24, display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center', gap: 18 }}>

          {/* "Thinking" mark */}
          <div style={{
            position: 'relative', width: 96, height: 96, borderRadius: 28,
            background: t.primaryContainer, color: t.onPrimaryContainer,
            display: 'grid', placeItems: 'center',
          }}>
            <span className="mi" style={{ fontSize: 48 }}>auto_awesome</span>
            <div style={{
              position: 'absolute', inset: -6, borderRadius: 32,
              border: `2px solid ${t.accent}`, opacity: 0.4,
            }} className="spin" />
          </div>

          <div>
            <div style={{ fontSize: 22, fontWeight: 500, color: t.onSurface, letterSpacing: -0.3 }}>
              Thinking on-device
              <span className="dot-pulse" style={{ marginLeft: 4, color: t.accent }}>
                <span></span><span></span><span></span>
              </span>
            </div>
            <div style={{ marginTop: 10, fontSize: 13, color: t.onSurfaceVar, maxWidth: 280, lineHeight: 1.5 }}>
              Reading 8,420 words and finding what matters. No data leaves your phone.
            </div>
          </div>

          {/* Skeleton "draft" */}
          <div style={{ width: '100%', marginTop: 8 }}>
            <Card t={t}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                {[100, 88, 94, 60].map((w, i) => (
                  <div key={i} className={i === 3 ? '' : 'shimmer'} style={{
                    height: 10, width: `${w}%`, borderRadius: 4,
                    background: i === 3 ? 'transparent' : t.surfaceContainerHighest,
                    opacity: i === 3 ? 0 : 1,
                  }} />
                ))}
              </div>
            </Card>
          </div>
        </div>

        <div style={{ flex: 1 }} />
      </ScreenPad>
    </>
  );
}

// ─────────────────────────────────────────────────────────────
// SCREEN 2D — TL;DR result (the payoff)
// ─────────────────────────────────────────────────────────────
function ScreenResult(t) {
  return (
    <>
      <AppBar t={t}
        leading={<IconBtn t={t} glyph="arrow_back" />}
        trailing={<IconBtn t={t} glyph="more_vert" />}
        title="" />
      <div style={{ flex: 1, overflow: 'auto', padding: '0 20px 20px' }}>

        {/* Video chip */}
        <div style={{
          display: 'flex', alignItems: 'center', gap: 12,
          padding: '10px 12px', borderRadius: 14,
          background: t.surfaceContainer, marginBottom: 18,
        }}>
          <div style={{
            width: 52, height: 36, borderRadius: 6, flexShrink: 0,
            background: t.surfaceContainerHighest,
            backgroundImage: `repeating-linear-gradient(135deg, ${t.outlineVar} 0 1px, transparent 1px 8px)`,
            display: 'grid', placeItems: 'center', color: t.onSurfaceVar,
          }}>
            <span className="mi" style={{ fontSize: 16 }}>play_arrow</span>
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontSize: 13, fontWeight: 500, color: t.onSurface, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              How to read a paper, fast
            </div>
            <div style={{ fontSize: 11, color: t.onSurfaceVar }}>Andrej Karpathy · 47:12</div>
          </div>
        </div>

        {/* Hero TL;DR */}
        <div style={{
          fontSize: 11, fontWeight: 700, color: t.accent,
          textTransform: 'uppercase', letterSpacing: 2.5, marginBottom: 10,
          fontFamily: 'Roboto Mono, monospace',
        }}>TL;DR</div>

        <div style={{
          fontSize: 22, lineHeight: 1.4, color: t.onSurface,
          fontWeight: 400, letterSpacing: -0.2,
          textWrap: 'pretty',
        }}>
          Skim every paper in three passes — title and abstract first, then figures and headings, finally the math only if it pays off. <span style={{ background: `linear-gradient(transparent 60%, ${t.accent}55 60%)` }}>Most papers deserve five minutes, not five hours.</span> Take notes in your own words to force comprehension over highlighting.
        </div>

        {/* Stat row */}
        <div style={{
          marginTop: 24, display: 'grid',
          gridTemplateColumns: '1fr 1fr 1fr', gap: 8,
        }}>
          {[
            { v: '47m', l: 'video' },
            { v: '8.4K', l: 'words in' },
            { v: '52', l: 'words out' },
          ].map((x, i) => (
            <div key={i} style={{
              padding: '12px 10px', borderRadius: 14,
              background: t.surfaceContainer, textAlign: 'center',
            }}>
              <div style={{ fontSize: 20, fontWeight: 600, color: t.onSurface, fontFamily: "'Roboto Flex', sans-serif", letterSpacing: -0.5 }}>{x.v}</div>
              <div style={{ fontSize: 11, color: t.onSurfaceVar, marginTop: 2, textTransform: 'uppercase', letterSpacing: 0.8 }}>{x.l}</div>
            </div>
          ))}
        </div>

        {/* Action row */}
        <div style={{ marginTop: 18, display: 'flex', gap: 8 }}>
          <TonalBtn t={t} icon="content_copy">Copy</TonalBtn>
          <TonalBtn t={t} icon="ios_share">Share</TonalBtn>
          <div style={{ flex: 1 }} />
          <button className="m3-btn" style={{
            width: 48, height: 48, borderRadius: 24, border: 'none',
            background: t.surfaceContainerHigh, color: t.onSurface,
            display: 'grid', placeItems: 'center',
          }}>
            <span className="mi" style={{ fontSize: 20 }}>refresh</span>
          </button>
        </div>

        {/* Footer note */}
        <div style={{
          marginTop: 22, fontSize: 11, color: t.onSurfaceVar,
          display: 'flex', alignItems: 'center', gap: 6,
          fontFamily: 'Roboto Mono, monospace',
        }}>
          <span className="mi" style={{ fontSize: 12, color: t.success }}>verified_user</span>
          Generated locally · 3.2s
        </div>
      </div>
    </>
  );
}

// ─────────────────────────────────────────────────────────────
// SCREEN 2E — Invalid YouTube URL
// ─────────────────────────────────────────────────────────────
function ScreenInvalidURL(t) {
  return (
    <>
      <AppBar t={t}
        leading={<TLDWLogo t={t} size={26} />}
        trailing={<IconBtn t={t} glyph="more_vert" />}
        title="" />
      <ScreenPad>
        <div style={{ marginTop: 8 }}>
          <div style={{ fontSize: 32, fontWeight: 500, lineHeight: 1.1, color: t.onSurface, letterSpacing: -0.8 }}>
            Paste a YouTube link
          </div>
        </div>

        {/* Input with error */}
        <div style={{ marginTop: 24 }}>
          <div style={{
            border: `2px solid ${t.error}`,
            borderRadius: 16, padding: '14px 14px 14px 52px',
            background: t.surface, position: 'relative',
            minHeight: 64, display: 'flex', alignItems: 'center',
          }}>
            <span className="mi" style={{
              position: 'absolute', left: 16, top: '50%', transform: 'translateY(-50%)',
              fontSize: 22, color: t.error,
            }}>link_off</span>
            <span style={{ color: t.onSurface, fontSize: 15, fontFamily: 'Roboto Mono, monospace', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', flex: 1 }}>
              https://example.com/blog/post-12
            </span>
            <span className="mi" style={{ fontSize: 20, color: t.error, marginLeft: 8 }}>error</span>
          </div>
          <div style={{ marginTop: 8, padding: '0 4px', display: 'flex', alignItems: 'center', gap: 6, color: t.error, fontSize: 12 }}>
            <span className="mi" style={{ fontSize: 14 }}>info</span>
            That doesn't look like a YouTube URL.
          </div>
        </div>

        {/* Helper card */}
        <Card t={t} style={{ marginTop: 20 }}>
          <div style={{ fontSize: 13, fontWeight: 600, color: t.onSurface, marginBottom: 10 }}>We accept links like</div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6, fontFamily: 'Roboto Mono, monospace', fontSize: 12 }}>
            <div style={{ color: t.onSurfaceVar }}>youtube.com/watch?v=…</div>
            <div style={{ color: t.onSurfaceVar }}>youtu.be/…</div>
            <div style={{ color: t.onSurfaceVar }}>m.youtube.com/watch?v=…</div>
          </div>
        </Card>

        <div style={{ flex: 1 }} />
        <FilledBtn t={t} full disabled icon="auto_awesome">Summarize</FilledBtn>
      </ScreenPad>
    </>
  );
}

// ─────────────────────────────────────────────────────────────
// SCREEN 2F — Transcript fetch failed (no captions)
// ─────────────────────────────────────────────────────────────
function ScreenNoCaptions(t) {
  return (
    <>
      <AppBar t={t}
        leading={<IconBtn t={t} glyph="arrow_back" />}
        trailing={<div />}
        title="" />
      <ScreenPad>
        <Card t={t} padded={false} style={{ overflow: 'hidden' }}>
          <div style={{
            height: 110, background: t.surfaceContainerHighest, position: 'relative',
            backgroundImage: `repeating-linear-gradient(135deg, ${t.outlineVar} 0 1px, transparent 1px 12px)`,
          }}>
            <div style={{ position: 'absolute', bottom: 8, right: 8, padding: '3px 8px', borderRadius: 4, background: 'rgba(0,0,0,.7)', color: '#fff', fontSize: 11, fontFamily: 'Roboto Mono, monospace' }}>14:33</div>
          </div>
          <div style={{ padding: 14 }}>
            <div style={{ fontSize: 14, fontWeight: 500, color: t.onSurface }}>Live music session — no narration</div>
            <div style={{ fontSize: 12, color: t.onSurfaceVar, marginTop: 2 }}>kexp · 88K views</div>
          </div>
        </Card>

        <div style={{ marginTop: 28, display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center', gap: 18 }}>
          <div style={{
            width: 88, height: 88, borderRadius: 26,
            background: t.errorContainer, color: t.onErrorContainer,
            display: 'grid', placeItems: 'center',
          }}>
            <span className="mi" style={{ fontSize: 44 }}>subtitles_off</span>
          </div>

          <div style={{ maxWidth: 290 }}>
            <div style={{ fontSize: 22, fontWeight: 500, color: t.onSurface, letterSpacing: -0.3 }}>
              No captions on this video
            </div>
            <div style={{ marginTop: 10, fontSize: 13, color: t.onSurfaceVar, lineHeight: 1.55 }}>
              The uploader didn't add captions and YouTube hasn't auto-generated any. Without text, TLDW has nothing to read.
            </div>
          </div>
        </div>

        <div style={{ flex: 1 }} />
        <div style={{ display: 'flex', gap: 12 }}>
          <div style={{ flex: 1 }}><TonalBtn t={t} full icon="open_in_new">Open in YouTube</TonalBtn></div>
          <div style={{ flex: 1 }}><FilledBtn t={t} full icon="link">Try another</FilledBtn></div>
        </div>
      </ScreenPad>
    </>
  );
}

// ─────────────────────────────────────────────────────────────
// SCREEN 2G — Generation failed
// ─────────────────────────────────────────────────────────────
function ScreenGenerationFailed(t) {
  return (
    <>
      <AppBar t={t}
        leading={<IconBtn t={t} glyph="arrow_back" />}
        trailing={<IconBtn t={t} glyph="close" />}
        title="" />
      <ScreenPad>
        <Card t={t} padded={false} style={{ overflow: 'hidden' }}>
          <div style={{
            height: 90, background: t.surfaceContainerHighest, position: 'relative',
            backgroundImage: `repeating-linear-gradient(135deg, ${t.outlineVar} 0 1px, transparent 1px 12px)`,
          }} />
          <div style={{ padding: 12 }}>
            <div style={{ fontSize: 13, fontWeight: 500, color: t.onSurface }}>How to read a paper, fast</div>
          </div>
        </Card>

        <div style={{ marginTop: 24, display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center', gap: 18 }}>
          <div style={{
            width: 96, height: 96, borderRadius: 28,
            background: t.errorContainer, color: t.onErrorContainer,
            display: 'grid', placeItems: 'center',
          }}>
            <span className="mi" style={{ fontSize: 48 }}>warning_amber</span>
          </div>

          <div style={{ maxWidth: 290 }}>
            <div style={{ fontSize: 22, fontWeight: 500, color: t.onSurface, letterSpacing: -0.3 }}>
              The model gave up
            </div>
            <div style={{ marginTop: 10, fontSize: 13, color: t.onSurfaceVar, lineHeight: 1.55 }}>
              Generation stalled at 78%. This sometimes happens with very long transcripts. Re-running usually works.
            </div>
          </div>

          <Card t={t} style={{ width: '100%', textAlign: 'left' }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10, fontFamily: 'Roboto Mono, monospace', fontSize: 11 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', color: t.onSurfaceVar }}>
                <span>error</span><span style={{ color: t.error }}>INFERENCE_TIMEOUT</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', color: t.onSurfaceVar }}>
                <span>tokens</span><span style={{ color: t.onSurface }}>6,432 / 8,192</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', color: t.onSurfaceVar }}>
                <span>elapsed</span><span style={{ color: t.onSurface }}>42.1s</span>
              </div>
            </div>
          </Card>
        </div>

        <div style={{ flex: 1 }} />
        <div style={{ display: 'flex', gap: 12 }}>
          <TonalBtn t={t} icon="bug_report">Report</TonalBtn>
          <div style={{ flex: 1 }}>
            <FilledBtn t={t} full icon="refresh">Try again</FilledBtn>
          </div>
        </div>
      </ScreenPad>
    </>
  );
}

Object.assign(window, {
  ScreenInitial, ScreenDownloading, ScreenDownloadFailed, ScreenNoInternet,
  ScreenEmpty, ScreenFetching, ScreenGenerating, ScreenResult,
  ScreenInvalidURL, ScreenNoCaptions, ScreenGenerationFailed,
});
