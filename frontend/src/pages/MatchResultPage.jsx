import { useParams } from 'react-router-dom';

// TODO: Implement in T33
export default function MatchResultPage() {
  const { id } = useParams();
  return (
    <div>
      <h1 className="text-2xl font-bold mb-4">Compatibility result</h1>
      <p className="text-gray-500">Match score for listing #{id}. Coming in T33.</p>
    </div>
  );
}
