import { useParams } from 'react-router-dom';

// TODO: Implement in T34
export default function ModerationDetailPage() {
  const { id } = useParams();
  return (
    <div>
      <h1 className="text-2xl font-bold mb-4">Review listing #{id}</h1>
      <p className="text-gray-500">Moderation detail with approve/reject. Coming in T34.</p>
    </div>
  );
}
