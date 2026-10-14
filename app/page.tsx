"use client";

import { useEffect, useMemo, useState } from "react";
import {
  Activity, ArrowDownRight, ArrowUpRight, Bell, Check, ChevronDown, CircleHelp,
  Clapperboard, Clock3, Command, ExternalLink, Eye, FileVideo2, Film, Gauge,
  LayoutDashboard, Menu, MessageSquare, MoreHorizontal, Play, Plus, Search,
  Settings2, ShieldCheck, Sparkles, Users, Video, X, Youtube, Zap
} from "lucide-react";

type Section = "Dashboard" | "Analytics" | "Content" | "Comments" | "Settings";
type LiveData = {
  connected: boolean;
  channel?: { id: string; title: string; thumbnail?: string; subscribers?: string; views?: string; videos?: string };
  analytics?: { views?: string; watchTime?: string; subscribers?: string; estimatedMinutes?: string; days?: string[]; values?: number[] };
  error?: string;
};

const nav: { name: Section; icon: typeof LayoutDashboard }[] = [
  { name: "Dashboard", icon: LayoutDashboard },
  { name: "Analytics", icon: Activity },
  { name: "Content", icon: Clapperboard },
  { name: "Comments", icon: MessageSquare },
  { name: "Settings", icon: Settings2 }
];
const demoVideos = [
  { title: "I tried this for 7 days…", kind: "Short", views: "18.4K", comments: "126", status: "Public", date: "Today", color: "coral", duration: "0:42" },
  { title: "The complete beginner guide", kind: "Video", views: "8.2K", comments: "84", status: "Public", date: "Yesterday", color: "violet", duration: "12:08" },
  { title: "Things I wish I knew earlier", kind: "Short", views: "6.9K", comments: "51", status: "Public", date: "Oct 11, 2026", color: "blue", duration: "0:58" },
  { title: "Behind the scenes — setup tour", kind: "Video", views: "3.1K", comments: "28", status: "Unlisted", date: "Oct 09, 2026", color: "green", duration: "8:21" }
];
const samplePoints = [30, 39, 35, 48, 43, 57, 52, 69, 61, 75, 72, 88, 81, 96, 87, 100, 92, 106, 98, 118, 112, 130, 121, 143, 133, 150, 145, 164, 156, 180];

function Metric({ label, value, change, icon: Icon, accent = "" }: { label: string; value: string; change?: string; icon: typeof Eye; accent?: string }) {
  return <article className="metric glass"><div className="metric-top"><span>{label}</span><span className={"metric-icon " + accent}><Icon size={17}/></span></div><div className="metric-value">{value}</div><div className="metric-foot">{change ? <span className="positive"><ArrowUpRight size={14}/>{change}</span> : <span className="muted">Last 28 days</span>}<span className="muted">vs previous 28 days</span></div></article>;
}
function Chart({ points = samplePoints }: { points?: number[] }) {
  const path = useMemo(() => {
    const p = points.length ? points : samplePoints;
    const max = Math.max(...p, 1);
    return p.map((n, i) => `${(i / Math.max(p.length - 1, 1)) * 700},${190 - (n / max) * 160}`).join(" ");
  }, [points]);
  return <svg className="chart-svg" viewBox="0 0 700 200" preserveAspectRatio="none" role="img" aria-label="Views over time">
    {[35, 80, 125, 170].map(y => <line key={y} x1="0" y1={y} x2="700" y2={y} className="grid-line"/>)}<polyline points={path} className="chart-area"/><polyline points={path} className="chart-line"/>
  </svg>;
}
function Thumbnail({ color, duration }: { color: string; duration: string }) {
  return <div className={"thumb " + color}><div className="thumb-play"><Play size={16} fill="currentColor"/></div><span>{duration}</span></div>;
}

export default function Home() {
  const [section, setSection] = useState<Section>("Dashboard");
  const [mobileNav, setMobileNav] = useState(false);
  const [search, setSearch] = useState("");
  const [range, setRange] = useState("28 days");
  const [live, setLive] = useState<LiveData>({ connected: false });
  const [loading, setLoading] = useState(true);
  const [notice, setNotice] = useState("");
  const [showAlerts, setShowAlerts] = useState(false);

  useEffect(() => {
    let active = true;
    fetch("/api/youtube/data").then(r => r.json()).then((d: LiveData) => { if (active) setLive(d); })
      .catch(() => { if (active) setLive({ connected: false }); })
      .finally(() => { if (active) setLoading(false); });
    const params = new URLSearchParams(window.location.search);
    if (params.get("connected") === "1") setNotice("YouTube channel connected. Live data is now available where permissions allow.");
    if (params.get("error")) setNotice("Connection could not be completed: " + params.get("error"));
    return () => { active = false; };
  }, []);

  const videos = demoVideos.filter(v => v.title.toLowerCase().includes(search.toLowerCase()));
  const connect = () => { window.location.href = "/api/youtube/connect"; };
  const pageTitle = section === "Dashboard" ? "Channel overview" : section;
  const displayViews = live.channel?.views ?? "—";
  const displaySubs = live.channel?.subscribers ?? "—";

  return <main className="app-shell">
    <aside className={"sidebar " + (mobileNav ? "sidebar-open" : "")}>
      <div className="brand"><div className="brand-mark"><Youtube size={22} fill="currentColor"/></div><div><strong>yt<span>dash</span></strong><small>CREATOR STUDIO</small></div><button className="icon-btn close-nav" onClick={() => setMobileNav(false)} aria-label="Close navigation"><X size={18}/></button></div>
      <button className="channel-switch" onClick={connect}><div className="channel-avatar">{live.channel?.thumbnail ? <img src={live.channel.thumbnail} alt="Channel avatar"/> : <Youtube size={19}/>}</div><div className="channel-text"><b>{live.channel?.title ?? "Your channel"}</b><span>{live.connected ? "Connected to YouTube" : "Connect a channel"}</span></div><ChevronDown size={15}/></button>
      <div className="nav-label">WORKSPACE</div>
      <nav className="nav-list">{nav.map(item => { const Icon = item.icon; return <button key={item.name} onClick={() => {setSection(item.name);setMobileNav(false);}} className={"nav-item " + (section === item.name ? "active" : "")}><Icon size={18}/><span>{item.name}</span>{item.name === "Comments" && <span className="nav-dot"/>}</button>; })}</nav>
      <div className="sidebar-bottom"><div className="plan-card"><div className="plan-icon"><Sparkles size={16}/></div><b>Your creative space</b><p>Everything you need to understand your channel and grow with intention.</p><button onClick={connect}>Connect YouTube <ExternalLink size={13}/></button></div><button className="nav-item help-link" onClick={() => setNotice("Ytdash is an independent creator dashboard. Connect a Google account to load your own channel data.")}><CircleHelp size={18}/><span>Help & feedback</span></button><div className="sidebar-foot"><span className="status-dot"/>{live.connected ? "YouTube connected" : "Demo workspace"}<span className="version">v1.0</span></div></div>
    </aside>

    {mobileNav && <button className="scrim" aria-label="Close menu" onClick={() => setMobileNav(false)}/>}
    <section className="main-area">
      <header className="topbar"><div className="top-left"><button className="icon-btn mobile-menu" onClick={() => setMobileNav(true)} aria-label="Open navigation"><Menu size={20}/></button><div className="crumb"><span>Workspace</span><span className="crumb-slash">/</span><b>{section}</b></div></div><div className="top-actions"><label className="search-box"><Search size={16}/><input value={search} onChange={e => {setSearch(e.target.value); if (section !== "Content") setSection("Content");}} placeholder="Search your studio"/><kbd>⌘ K</kbd></label><button className="icon-btn alert-button" onClick={() => setShowAlerts(!showAlerts)} aria-label="Notifications"><Bell size={18}/><i/></button>{showAlerts && <div className="alerts-popover"><b>You're all caught up</b><p>Channel updates and important alerts will appear here.</p></div>}<button className="top-avatar" onClick={() => setSection("Settings")} aria-label="Open settings">{live.channel?.thumbnail ? <img src={live.channel.thumbnail} alt="Channel"/> : <span>Y</span>}</button></div></header>

      <div className="page-content">
        {notice && <div className="notice"><Check size={16}/><span>{notice}</span><button onClick={() => {setNotice("");window.history.replaceState({}, "", window.location.pathname);}} aria-label="Dismiss"><X size={16}/></button></div>}
        <div className="page-heading"><div><div className="eyebrow"><span className="eyebrow-dot"/>{live.connected ? "YOUR CHANNEL" : "CREATOR WORKSPACE"}</div><h1>{pageTitle}<span className="heading-period">.</span></h1><p>{section === "Dashboard" ? "A clear picture of how your content is doing." : section === "Analytics" ? "Understand what is working and where viewers find you." : section === "Content" ? "Manage and review your published videos." : section === "Comments" ? "Keep your community conversations moving." : "Make Ytdash work the way you do."}</p></div><div className="heading-actions">{section !== "Settings" && <button className="select-button" onClick={() => setRange(range === "28 days" ? "7 days" : "28 days")}><Clock3 size={15}/>{range}<ChevronDown size={14}/></button>}<button className="primary-button" onClick={connect}><Plus size={17}/>{live.connected ? "Refresh connection" : "Connect YouTube"}</button></div></div>

        {!live.connected && <div className="demo-banner"><div className="demo-symbol"><Zap size={17}/></div><div><b>You're viewing a preview workspace</b><span>Sample cards show the layout. Connect YouTube to load channel details and authorized analytics.</span></div><button onClick={connect}>Connect channel <ArrowUpRight size={15}/></button></div>}

        {section === "Dashboard" && <>
          <div className="metrics-grid"><Metric label="Views" value={live.connected ? displayViews : "24.8K"} change={live.connected ? undefined : "+18.6%"} icon={Eye} accent="blue-accent"/><Metric label="Watch time (hours)" value={live.connected ? (live.analytics?.watchTime ?? "—") : "1.6K"} change={live.connected ? undefined : "+12.4%"} icon={Clock3} accent="purple-accent"/><Metric label="Subscribers" value={live.connected ? displaySubs : "+284"} change={live.connected ? undefined : "+8.2%"} icon={Users} accent="green-accent"/><Metric label="Published videos" value={live.connected ? (live.channel?.videos ?? "—") : "32"} icon={Video} accent="orange-accent"/></div>
          <div className="content-grid"><article className="panel chart-panel"><div className="panel-heading"><div><h2>Performance</h2><p>{live.connected ? "Your channel analytics" : "A snapshot of your channel's momentum"}</p></div><button className="subtle-button" onClick={() => setSection("Analytics")}>View analytics <ArrowUpRight size={14}/></button></div><div className="chart-legend"><span className="legend-dot"/><b>Views</b><span className="legend-note">{live.connected ? "YouTube Analytics" : "Sample data"}</span></div><div className="chart-wrap"><Chart points={live.analytics?.values?.length ? live.analytics.values : samplePoints}/></div><div className="chart-axis"><span>28 days ago</span><span>21 days</span><span>14 days</span><span>7 days</span><span>Today</span></div></article>
          <article className="panel realtime-panel"><div className="panel-heading"><div><h2>Realtime</h2><p>Live channel activity</p></div><span className="live-indicator"><span/>LIVE</span></div><div className="realtime-number">{live.connected ? (live.channel?.views ?? "—") : "1,248"}</div><div className="muted small">Total channel views{live.connected ? "" : " · sample preview"}</div><div className="mini-bars">{[30,46,37,55,43,66,48,75,62,83,55,72,91,69,85,61,100,77,88,63,94,74,82,57].map((h,i)=><span key={i} style={{height:h+"%"}}/>)}</div><div className="realtime-footer"><span><span className="status-dot"/>Activity overview</span><button onClick={() => setSection("Analytics")}>Details <ArrowUpRight size={13}/></button></div></article></div>
          <article className="panel recent-panel"><div className="panel-heading"><div><h2>Latest content</h2><p>See how your recent uploads are performing</p></div><button className="subtle-button" onClick={() => setSection("Content")}>View all content <ArrowUpRight size={14}/></button></div><VideoTable videos={videos.slice(0,3)}/></article>
          <div className="bottom-grid"><article className="panel tip-panel"><div className="tip-orb"><Sparkles size={19}/></div><div><span className="eyebrow">CREATOR NOTE</span><h3>Consistency beats guesswork.</h3><p>Review your best-performing videos, learn what held attention, then bring that insight into your next upload.</p></div><button className="round-arrow" onClick={() => setSection("Analytics")} aria-label="Open analytics"><ArrowUpRight size={18}/></button></article><article className="panel quick-panel"><h2>Quick actions</h2><button onClick={connect}><span className="quick-icon"><Youtube size={17}/></span><span><b>Connect your channel</b><small>Unlock authorized YouTube data</small></span><ArrowUpRight size={15}/></button><button onClick={() => setSection("Content")}><span className="quick-icon"><FileVideo2 size={17}/></span><span><b>Review your content</b><small>Explore video management</small></span><ArrowUpRight size={15}/></button></article></div>
        </>}

        {section === "Analytics" && <><div className="metrics-grid"><Metric label="Views" value={live.connected ? displayViews : "24.8K"} change={live.connected ? undefined : "+18.6%"} icon={Eye}/><Metric label="Watch time" value={live.connected ? (live.analytics?.watchTime ?? "—") : "1.6K hrs"} change={live.connected ? undefined : "+12.4%"} icon={Clock3}/><Metric label="Subscribers" value={live.connected ? displaySubs : "+284"} change={live.connected ? undefined : "+8.2%"} icon={Users}/><Metric label="Estimated revenue" value="—" icon={Gauge}/></div><article className="panel chart-panel analytics-chart"><div className="panel-heading"><div><h2>Views over time</h2><p>{live.connected ? "Connected channel · last 28 days" : "Preview data · connect your channel for actual analytics"}</p></div><span className="pill"><Activity size={14}/> Overview</span></div><Chart points={live.analytics?.values?.length ? live.analytics.values : samplePoints}/><div className="chart-axis"><span>Sep 17</span><span>Sep 24</span><span>Oct 01</span><span>Oct 08</span><span>Oct 14</span></div></article><div className="content-grid"><article className="panel"><div className="panel-heading"><div><h2>Audience</h2><p>Subscriber movement</p></div></div><div className="empty-visual"><Users size={24}/><b>{live.connected ? displaySubs : "+284"} subscribers</b><span>{live.connected ? "Current channel total" : "Illustrative preview total"}</span></div></article><article className="panel"><div className="panel-heading"><div><h2>Top traffic sources</h2><p>Where viewers discover you</p></div></div>{["YouTube search","Browse features","Suggested videos","External"].map((s,i)=><div className="source-row" key={s}><span>{s}</span><div className="source-track"><i style={{width:[76,63,48,21][i]+"%"}}/></div><b>{[42,31,19,8][i]}%</b></div>)}{!live.connected && <span className="sample-caption">Illustrative sample only</span>}</article></div></>}

        {section === "Content" && <article className="panel content-table-panel"><div className="panel-heading"><div><h2>Your videos</h2><p>Published videos and recent uploads</p></div><button className="primary-button" onClick={() => setNotice("Video upload integration is prepared for YouTube OAuth. Connect your channel and configure the required YouTube API permissions.")}><Plus size={16}/>Upload video</button></div><div className="filter-row"><div className="filter-chip active">All content</div><div className="filter-chip">Videos</div><div className="filter-chip">Shorts</div><div className="filter-chip">Live</div><span className="table-search"><Search size={14}/>{search || "Search titles above"}</span></div><VideoTable videos={videos}/>{live.connected && <p className="integration-note">Your channel is connected. Detailed video listing and upload actions require the YouTube Data API integration to be enabled in Google Cloud.</p>}</article>}

        {section === "Comments" && <article className="panel comments-panel"><div className="panel-heading"><div><h2>Community inbox</h2><p>Conversation management for your channel</p></div><span className="pill"><MessageSquare size={14}/> Comments</span></div><div className="empty-state"><div className="empty-icon"><MessageSquare size={23}/></div><h3>{live.connected ? "Comment inbox is ready to connect" : "Your community starts here"}</h3><p>{live.connected ? "Enable the YouTube Data API and comments scope to load, reply to, and moderate channel comments." : "Connect YouTube to bring channel conversations into one calm workspace."}</p><button className="primary-button" onClick={connect}><Youtube size={16}/> {live.connected ? "Refresh permissions" : "Connect YouTube"}</button></div></article>}

        {section === "Settings" && <div className="settings-stack"><article className="panel settings-card"><div className="settings-icon"><Youtube size={20}/></div><div className="settings-copy"><h2>YouTube connection</h2><p>Authorize Ytdash to read channel details and analytics. Access is controlled by Google OAuth.</p><div className="connection-state"><span className={"status-dot " + (live.connected ? "" : "offline")}/>{live.connected ? "Channel connected" : "Not connected"}</div></div><button className="primary-button" onClick={connect}>{live.connected ? "Reconnect" : "Connect channel"}</button></article><article className="panel settings-card"><div className="settings-icon"><ShieldCheck size={20}/></div><div className="settings-copy"><h2>Privacy & security</h2><p>OAuth tokens are intended to be encrypted at rest on the server. Never add service-role keys or Google client secrets to public frontend variables.</p><span className="muted small">Ytdash is an independent application and is not affiliated with Google.</span></div></article><article className="panel settings-card"><div className="settings-icon"><Command size={20}/></div><div className="settings-copy"><h2>Workspace</h2><p>Ytdash Creator Studio · Version 1.0.0</p><span className="muted small">Apple-inspired dark interface · Responsive layout</span></div></article></div>}
        <footer className="page-footer"><span>© 2026 Ytdash Studio</span><span><span className="status-dot"/> {loading ? "Checking connection…" : live.connected ? "Connected to YouTube" : "Preview mode"}</span><span>Made for creators, with focus.</span></footer>
      </div>
    </section>
  </main>;
}

function VideoTable({ videos }: { videos: typeof demoVideos }) {
  return <div className="video-table-wrap"><table className="video-table"><thead><tr><th>Video</th><th>Visibility</th><th>Views</th><th>Comments</th><th>Date</th><th></th></tr></thead><tbody>{videos.map((v,i)=><tr key={v.title}><td><div className="video-cell"><Thumbnail color={v.color} duration={v.duration}/><div><b>{v.title}</b><span>{v.kind} · {i===0?"Updated recently":"Published"}</span></div></div></td><td><span className={"visibility " + (v.status==="Public"?"public":"private")}><span/>{v.status}</span></td><td className="number-cell">{v.views}</td><td className="number-cell">{v.comments}</td><td className="date-cell">{v.date}</td><td><button className="icon-btn row-more" aria-label={"More actions for "+v.title}><MoreHorizontal size={17}/></button></td></tr>)}</tbody></table>{videos.length===0 && <div className="no-results">No videos match your search.</div>}</div>;
}