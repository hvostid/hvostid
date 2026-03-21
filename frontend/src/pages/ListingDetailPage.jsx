import { useParams } from 'react-router-dom';

// TODO: Implement in T31
export default function ListingDetailPage() {
  const { id } = useParams();
  return (
    <div>
      <h1 className="text-2xl font-bold mb-4">Listing #{id}</h1>
      <p className="text-gray-500">Listing detail with passport and trust score. Coming in T31.</p>
    </div>
  );
}
