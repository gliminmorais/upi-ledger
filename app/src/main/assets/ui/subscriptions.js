'use strict';

function subscriptionHome(){
  const active=(data.subscriptions||[]).filter(s=>s.active);
  return `<button class="vendor-card glass" style="margin-top:15px" data-action="subscriptions"><span class="mini-icon lilac">${icon('subscriptions')}</span><span class="payment-main"><strong>Monthly subscriptions</strong><span class="payment-meta">${active.length?`${active.length} active · ${money(active.reduce((n,s)=>n+s.paise,0))} / month`:'Add your recurring services'}</span></span>${icon('chevron','arrow')}</button>`;
}

function subscriptions(){
  const plans=data.subscriptions||[],active=plans.filter(s=>s.active).sort((a,b)=>a.nextDue.localeCompare(b.nextDue)),cancelled=plans.filter(s=>!s.active),total=active.reduce((n,s)=>n+s.paise,0);
  const tile=s=>`<div class="card subscription-card"><button class="payment-row" data-action="editSubscription" data-id="${s.id}"><span class="vendor-avatar" style="${color('Subscription')}">${esc(initials(s.name))}</span><span class="payment-main"><strong>${esc(s.name)}</strong><span class="payment-meta">${s.active?'Next: '+shortDate(s.nextDue):'Cancelled'} · Day ${s.renewalDay}</span></span><span class="payment-right">${money(s.paise)}<small>/ month</small></span></button><div class="chart-top"><span class="tag">Subscription</span>${s.active?`<button class="text-button" data-action="cancelSubscription" data-id="${s.id}">Cancel subscription</button>`:`<button class="text-button" data-action="editSubscription" data-id="${s.id}">Edit / restart</button>`}</div></div>`;
  return heading('YOUR RECURRING ROUTINE','Monthly plans.<br><em>Always in view.</em>')+`<div class="hero glass"><div class="hero-label">Active monthly commitments${icon('subscriptions')}</div><div class="amount">${money(total,true)}</div><div class="hero-footer"><span class="change">${active.length} active subscriptions</span><span class="tiny">${money(total*12)} / year</span></div></div><button class="primary full" data-action="newSubscription">${icon('plus')} Create subscription</button><div class="info-strip">${icon('calendar')}<span>Ledger records due renewals when you open it, including missed months. Entries appear as debits on Home under <b>Subscription</b>. This does not check or move money in your bank.</span></div>${section('Active subscriptions')}${active.length?active.map(tile).join(''):`<div class="card">${empty('Set it up once','Add Spotify, Netflix or another monthly service. No screenshot is needed.')}</div>`}${cancelled.length?section('Cancelled subscriptions')+cancelled.map(tile).join(''):''}`;
}

function editSubscription(id=0){
  const sub=(data.subscriptions||[]).find(s=>s.id===id)||{id:0,name:'',paise:0,nextDue:data.today,renewalDay:LedgerModel.day(data.today).getDate(),active:true,remarks:''};
  sheet(sub.id?'Edit subscription':'Create subscription',`<form id="subscriptionForm" data-id="${sub.id}">${field('Subscription name / vendor','name',sub.name,'text','required maxlength="200" placeholder="e.g. Spotify"')}${field('Monthly amount (₹)','amount',sub.paise?(sub.paise/100).toFixed(2):'','text','inputmode="decimal" required')}<div class="form-grid">${field('Next renewal date','nextDue',sub.nextDue,'date','required')}${field('Renewal day (1–31)','renewalDay',sub.renewalDay,'number','required min="1" max="31" step="1"')}</div><p class="hint">A renewal on day 29, 30 or 31 uses the last day of a shorter month, then returns to your chosen day.</p><label class="field">Category<input value="Subscription" readonly></label><label class="field">Remarks · optional<textarea name="remarks" maxlength="1900">${esc(sub.remarks)}</textarea></label><label class="check"><input name="active" type="checkbox" ${sub.active?'checked':''}><span>Active — record monthly on this date</span></label><div class="info-strip">${icon('calendar')}<span>Due dates up to today are recorded immediately when saved or opened. Future months are added when you next open Ledger. Cancel in Subscriptions to stop future entries.</span></div><button class="primary full" type="submit" id="saveSubscriptionButton">${sub.id?'Save changes':'Create subscription'}</button></form>`);
}

function submitSubscription(form){
  if(!form.reportValidity())return;
  const values=Object.fromEntries(new FormData(form));
  if(!/^\d+(\.\d{1,2})?$/.test(values.amount.trim())||Number(values.amount)<=0)return notify('Enter a positive monthly amount with up to 2 decimal places.');
  const payload={...values,id:Number(form.dataset.id),renewalDay:Number(values.renewalDay),active:form.elements.active.checked};
  if(native){$('#saveSubscriptionButton').disabled=true;native.saveSubscription(JSON.stringify(payload));}
  else{
    if((data.subscriptions||[]).some(s=>s.id!==payload.id&&s.name.toLowerCase()===payload.name.trim().toLowerCase()))return notify('That subscription already exists.');
    const sub={...payload,name:payload.name.trim(),id:payload.id||Date.now(),paise:Math.round(Number(values.amount)*100)};
    data.subscriptions=(data.subscriptions||[]).filter(s=>s.id!==sub.id);data.subscriptions.push(sub);postPreviewSubscriptions();render();window.savedSubscription();
  }
}

function cancelSubscription(id){
  if(native)native.cancelSubscription(id);
  else sheet('Cancel subscription?',`<p class="subtitle">This stops future entries in Ledger. Past payments remain. It does not cancel the actual service or its bank autopay.</p><button class="primary full" data-action="confirmCancelSubscription" data-id="${id}">Cancel subscription</button>`);
}

function postPreviewSubscriptions(){
  for(const sub of data.subscriptions||[]){
    for(const due of LedgerModel.dueRenewals(sub,data.today)){
      let v=data.vendors.find(v=>v.name.toLowerCase()===sub.name.toLowerCase());
      if(!v){v={id:Date.now()+Math.random(),name:sub.name,category:'Subscription'};data.vendors.push(v);}v.category='Subscription';
      if(!data.entries.some(e=>e.subscriptionId===sub.id&&e.subscriptionDue===due))data.entries.push({id:Date.now()+Math.random(),vendorId:v.id,vendor:sub.name,paise:sub.paise,category:'Subscription',date:due,time:'00:00',remarks:sub.remarks||'Automatically recorded monthly subscription',reference:'',source:'Subscription',subscriptionId:sub.id,subscriptionDue:due});
      sub.nextDue=LedgerModel.nextRenewal(due,sub.renewalDay);
    }
  }
  data.entries.sort((a,b)=>b.date.localeCompare(a.date)||b.time.localeCompare(a.time));
}

window.savedSubscription=()=>{closeSheet();notify('Subscription saved. Due entries are up to date.');};
document.addEventListener('click',event=>{
  const b=event.target.closest('[data-action]');if(!b)return;const id=Number(b.dataset.id);
  switch(b.dataset.action){
    case 'newSubscription':editSubscription();break;
    case 'editSubscription':editSubscription(id);break;
    case 'cancelSubscription':cancelSubscription(id);break;
    case 'confirmCancelSubscription':{const sub=(data.subscriptions||[]).find(s=>s.id===id);if(sub)sub.active=false;closeSheet();render();notify('Subscription cancelled in Ledger.');break;}
  }
});
document.addEventListener('submit',event=>{if(event.target.id==='subscriptionForm'){event.preventDefault();submitSubscription(event.target);}});
document.addEventListener('change',event=>{
  const f=$('#subscriptionForm');if(!f)return;
  if(event.target.name==='nextDue')f.elements.renewalDay.value=LedgerModel.day(event.target.value).getDate();
  if(event.target.name==='renewalDay'){
    const d=LedgerModel.day(f.elements.nextDue.value),day=Number(event.target.value);
    if(day>=1&&day<=31){d.setDate(Math.min(day,new Date(d.getFullYear(),d.getMonth()+1,0).getDate()));f.elements.nextDue.value=LedgerModel.iso(d);}
  }
});
document.addEventListener('DOMContentLoaded',()=>{
  if(demo){data.subscriptions=[{id:1,name:'Spotify',paise:11900,nextDue:'2026-10-28',renewalDay:28,active:true,remarks:''},{id:2,name:'Netflix',paise:19900,nextDue:'2026-11-02',renewalDay:2,active:true,remarks:''}];render();}
});
