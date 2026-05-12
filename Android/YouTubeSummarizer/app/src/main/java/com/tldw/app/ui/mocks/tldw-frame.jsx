// tldw-frame.jsx — Custom Android M3 frame for TLDW
// Light/dark aware, with status bar + nav pill. App bar is rendered inside
// the screen content (so we can vary it per state).

const TLDW_TOKENS = {
  light: {
    bg: '#fefbf6',           // surface
    surface: '#fefbf6',
    surfaceContainer: '#f4eee4',
    surfaceContainerHigh: '#ede7dd',
    surfaceContainerHighest: '#e7e1d8',
    onSurface: '#1d1c14',
    onSurfaceVar: '#4c4838',
    outline: '#7e7866',
    outlineVar: '#cfc8b6',
    primary: '#6b5410',       // amber-brown
    onPrimary: '#ffffff',
    primaryContainer: '#f5dfa3',
    onPrimaryContainer: '#231a00',
    accent: '#f4b400',        // the warm amber accent
    error: '#ba1a1a',
    onError: '#ffffff',
    errorContainer: '#ffdad6',
    onErrorContainer: '#410002',
    success: '#4f6516',
    successContainer: '#d2ec8b',
    onSuccessContainer: '#151f00',
    inverseSurface: '#322f27',
    inverseOnSurface: '#f5f0e6',
    scrim: 'rgba(0,0,0,.32)',
    frame: 'rgba(116,119,117,0.5)',
  },
  dark: {
    bg: '#15140d',
    surface: '#15140d',
    surfaceContainer: '#211f17',
    surfaceContainerHigh: '#2c2920',
    surfaceContainerHighest: '#37342a',
    onSurface: '#e8e2d4',
    onSurfaceVar: '#cfc8b6',
    outline: '#989282',
    outlineVar: '#4c4838',
    primary: '#f5c84a',
    onPrimary: '#3a2c00',
    primaryContainer: '#534008',
    onPrimaryContainer: '#f5dfa3',
    accent: '#f4b400',
    error: '#ffb4ab',
    onError: '#690005',
    errorContainer: '#93000a',
    onErrorContainer: '#ffdad6',
    success: '#b6cf72',
    successContainer: '#384c00',
    onSuccessContainer: '#d2ec8b',
    inverseSurface: '#e8e2d4',
    inverseOnSurface: '#322f27',
    scrim: 'rgba(0,0,0,.6)',
    frame: 'rgba(0,0,0,0.6)',
  },
};

// Status bar
function TLDWStatusBar({ t }) {
  const c = t.onSurface;
  return (
    <div style={{
      height: 36, display: 'flex', alignItems: 'center',
      justifyContent: 'space-between', padding: '0 18px 0 22px',
      position: 'relative', flexShrink: 0,
      fontFamily: 'Roboto, system-ui, sans-serif',
    }}>
      <span style={{ fontSize: 14, fontWeight: 500, color: c, letterSpacing: 0.1 }}>9:41</span>
      <div style={{
        position: 'absolute', left: '50%', top: 8, transform: 'translateX(-50%)',
        width: 22, height: 22, borderRadius: 100, background: '#000',
      }} />
      <div style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
        {/* signal */}
        <svg width="15" height="15" viewBox="0 0 16 16">
          <path d="M2 11h2v3H2zM6 8h2v6H6zM10 5h2v9h-2zM14 2h-2v12h2z" fill={c}/>
        </svg>
        {/* wifi */}
        <svg width="15" height="15" viewBox="0 0 16 16">
          <path d="M8 13.3L.67 5.97a10.37 10.37 0 0114.66 0L8 13.3z" fill={c}/>
        </svg>
        {/* battery */}
        <svg width="22" height="12" viewBox="0 0 22 12">
          <rect x="0.5" y="0.5" width="19" height="11" rx="2.5" fill="none" stroke={c} strokeOpacity="0.5"/>
          <rect x="2" y="2" width="14" height="8" rx="1" fill={c}/>
          <rect x="20" y="4" width="1.5" height="4" rx="0.5" fill={c} fillOpacity="0.5"/>
        </svg>
      </div>
    </div>
  );
}

// Gesture nav pill
function TLDWNavBar({ t }) {
  return (
    <div style={{
      height: 28, display: 'flex', alignItems: 'center', justifyContent: 'center',
      flexShrink: 0,
    }}>
      <div style={{
        width: 124, height: 4, borderRadius: 2,
        background: t.onSurface, opacity: 0.6,
      }} />
    </div>
  );
}

// Device frame — content fills between status & nav bars
function TLDWFrame({ children, dark = false, width = 380, height = 780 }) {
  const t = dark ? TLDW_TOKENS.dark : TLDW_TOKENS.light;
  return (
    <div style={{
      width, height, borderRadius: 36, overflow: 'hidden',
      background: t.bg,
      border: `8px solid ${t.frame}`,
      boxShadow: dark
        ? '0 24px 60px rgba(0,0,0,.45), 0 4px 12px rgba(0,0,0,.2)'
        : '0 24px 60px rgba(60,40,10,.18), 0 4px 12px rgba(60,40,10,.08)',
      display: 'flex', flexDirection: 'column', boxSizing: 'border-box',
      color: t.onSurface,
      fontFamily: "'Roboto Flex', 'Roboto', system-ui, sans-serif",
    }}>
      <TLDWStatusBar t={t} />
      <div style={{ flex: 1, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
        {typeof children === 'function' ? children(t) : children}
      </div>
      <TLDWNavBar t={t} />
    </div>
  );
}

Object.assign(window, { TLDWFrame, TLDW_TOKENS });
