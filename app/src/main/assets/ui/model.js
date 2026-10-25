(function(scope){
  const iso=d=>`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
  const day=s=>new Date(s+'T12:00:00');
  function range(today,period,month){const t=day(today);let start,end,previousStart,previousEnd;if(period==='week'){start=new Date(t);start.setDate(t.getDate()-((t.getDay()+6)%7));end=new Date(t);end.setDate(end.getDate()+1);previousStart=new Date(start);previousStart.setDate(start.getDate()-7);previousEnd=new Date(end);previousEnd.setDate(end.getDate()-7);}else if(period==='all'){return {start:'0000-01-01',end:'9999-12-31',previousStart:'0000-01-01',previousEnd:'0000-01-01'};}else{const [y,m]=(month||today.slice(0,7)).split('-').map(Number);start=new Date(y,m-1,1,12);end=new Date(y,m,1,12);previousStart=new Date(y,m-2,1,12);previousEnd=new Date(y,m-1,1,12);if(month===today.slice(0,7)||!month){end=new Date(t);end.setDate(end.getDate()+1);const count=Math.min(t.getDate(),new Date(y,m-1,0).getDate());previousEnd=new Date(y,m-2,count+1,12);}}return Object.fromEntries(Object.entries({start,end,previousStart,previousEnd}).map(([k,v])=>[k,iso(v)]));}
  function summary(entries,r){const selected=entries.filter(e=>e.date>=r.start&&e.date<r.end);const previous=entries.filter(e=>e.date>=r.previousStart&&e.date<r.previousEnd).reduce((n,e)=>n+e.paise,0);const total=selected.reduce((n,e)=>n+e.paise,0);const groups=key=>{const m=new Map;selected.forEach(e=>m.set(e[key],(m.get(e[key])||0)+e.paise));return [...m].sort((a,b)=>b[1]-a[1]);};return {entries:selected,total,previous,change:previous?100*(total-previous)/previous:null,categories:groups('category'),vendors:groups('vendor')};}
  function series(entries,start,end){let out=[],d=day(start),last=day(end);if(!Number.isFinite(+d)||last-d>370*86400000)return [];for(;d<last;d.setDate(d.getDate()+1)){const date=iso(d);out.push({date,value:entries.filter(e=>e.date===date).reduce((n,e)=>n+e.paise,0)});}return out;}
  function vendorRange(today,period,month,anchor){
    if(period!=='week')return range(today,period,month);
    const r=range(anchor||today,'week');const end=day(r.start);end.setDate(end.getDate()+7);
    return {...r,end:iso(end)};
  }
  function nextRenewal(date,renewalDay){if(!Number.isInteger(renewalDay)||renewalDay<1||renewalDay>31)throw new Error('Invalid renewal day');const from=day(date),next=new Date(from.getFullYear(),from.getMonth()+1,1,12);next.setDate(Math.min(renewalDay,new Date(next.getFullYear(),next.getMonth()+1,0).getDate()));return iso(next);}
  function dueRenewals(sub,today){if(!sub.active)return [];const dates=[];let next=sub.nextDue;while(next<=today){if(dates.length>=1200)throw new Error('Subscription history too long');dates.push(next);next=nextRenewal(next,sub.renewalDay);}return dates;}
  const api={iso,day,range,summary,series,vendorRange,nextRenewal,dueRenewals};if(typeof module!=='undefined')module.exports=api;scope.LedgerModel=api;
})(typeof window==='undefined'?globalThis:window);
