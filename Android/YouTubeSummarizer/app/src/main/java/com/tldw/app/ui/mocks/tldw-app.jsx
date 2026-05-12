// tldw-app.jsx — assembles the canvas with all 11 screens

const { useState, useEffect } = React;

const SCREENS = [
  { id: 'initial',     label: '1.A · Initial',           comp: ScreenInitial,           note: 'Pre-download — single primary CTA' },
  { id: 'downloading', label: '1.B · Downloading',       comp: ScreenDownloading,       note: 'Determinate progress, on-device messaging' },
  { id: 'dl-failed',   label: '1.C · Download failed',   comp: ScreenDownloadFailed,    note: 'Resumable error with diagnostic chip' },
  { id: 'offline',     label: '1.D · No internet',       comp: ScreenNoInternet,        note: 'Connection checklist + settings deep-link' },

  { id: 'empty',       label: '2.A · Empty',             comp: ScreenEmpty,             note: 'Paste-URL hero with example videos' },
  { id: 'fetching',    label: '2.B · Fetching',          comp: ScreenFetching,          note: 'Three-step pipeline indicator' },
  { id: 'generating',  label: '2.C · Generating',        comp: ScreenGenerating,        note: 'On-device thinking + skeleton draft' },
  { id: 'result',      label: '2.D · TL;DR result',      comp: ScreenResult,            note: 'The payoff screen — short paragraph' },

  { id: 'bad-url',     label: '2.E · Invalid URL',       comp: ScreenInvalidURL,        note: 'Inline field error + accepted formats' },
  { id: 'no-captions', label: '2.F · No captions',       comp: ScreenNoCaptions,        note: 'Fallback when transcript is missing' },
  { id: 'gen-failed',  label: '2.G · Generation failed', comp: ScreenGenerationFailed,  note: 'Recoverable model error w/ telemetry' },
];

const SECTIONS = [
  { id: 'screen-1', title: 'Screen 1 — Model setup', subtitle: 'First-run download flow + connectivity errors',
    ids: ['initial', 'downloading', 'dl-failed', 'offline'] },
  { id: 'screen-2', title: 'Screen 2 — Summarize', subtitle: 'Paste, fetch, generate, payoff (+ error states)',
    ids: ['empty', 'fetching', 'generating', 'result'] },
  { id: 'errors-2', title: 'Screen 2 — Error states', subtitle: 'Field validation, missing captions, model failure',
    ids: ['bad-url', 'no-captions', 'gen-failed'] },
];

// ─── Tweakable defaults
const TWEAK_DEFAULTS = /*EDITMODE-BEGIN*/{
  "darkMode": false,
  "frameWidth": 380,
  "frameHeight": 800
}/*EDITMODE-END*/;

function App() {
  const [tweaks, setTweak] = useTweaks(TWEAK_DEFAULTS);
  const dark = !!tweaks.darkMode;
  const W = tweaks.frameWidth;
  const H = tweaks.frameHeight;

  return (
    <>
      <DesignCanvas>
        {SECTIONS.map(sec => (
          <DCSection key={sec.id} id={sec.id} title={sec.title} subtitle={sec.subtitle}>
            {sec.ids.map(sid => {
              const s = SCREENS.find(x => x.id === sid);
              return (
                <DCArtboard key={s.id} id={s.id} label={s.label}
                  width={W + 16} height={H + 16}>
                  <div style={{
                    width: '100%', height: '100%',
                    display: 'grid', placeItems: 'center',
                    background: dark ? '#0e0d08' : '#f4f0e7',
                  }}>
                    <TLDWFrame dark={dark} width={W} height={H}>
                      {(t) => s.comp(t)}
                    </TLDWFrame>
                  </div>
                </DCArtboard>
              );
            })}
          </DCSection>
        ))}
      </DesignCanvas>

      <TweaksPanel title="Tweaks">
        <TweakSection label="Theme" />
        <TweakToggle label="Dark mode"
          value={tweaks.darkMode}
          onChange={v => setTweak('darkMode', v)} />
        <TweakSection label="Frame size" />
        <TweakSlider label="Width" min={340} max={440} step={4} unit="px"
          value={tweaks.frameWidth}
          onChange={v => setTweak('frameWidth', v)} />
        <TweakSlider label="Height" min={700} max={900} step={4} unit="px"
          value={tweaks.frameHeight}
          onChange={v => setTweak('frameHeight', v)} />
      </TweaksPanel>
    </>
  );
}

ReactDOM.createRoot(document.getElementById('root')).render(<App />);
