import { useState } from 'react';
import Sidebar from '@/components/Sidebar';
import WeeklyCalendar from '@/features/calendar/components/WeeklyCalendar';
import EventDrawer from '@/features/calendar/components/EventDrawer';
import EventSummary from '@/features/calendar/components/EventSummary';

export default function Calendario() {
  // Drawer lateral: aberto pela seta dos blocos ({ ev, dias }).
  const [drawer, setDrawer] = useState(null);

  return (
    <div className="app">
      <Sidebar ativa="calendar" />
      <main className="app__main app__main--calendar">
        <WeeklyCalendar onDrawer={(ev, dias) => setDrawer({ ev, dias })} />
      </main>
      {drawer && (
        <EventDrawer key={drawer.ev.id} onClose={() => setDrawer(null)}>
          <EventSummary ev={drawer.ev} dias={drawer.dias} />
        </EventDrawer>
      )}
    </div>
  );
}
