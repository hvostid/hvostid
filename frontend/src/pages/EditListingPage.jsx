import { useParams } from 'react-router-dom';

// TODO: Implement in T32
export default function EditListingPage() {
  const { id } = useParams();
  return (
    <div>
      <h1 className="text-2xl font-bold mb-4">Edit listing #{id}</h1>
      <p className="text-gray-500">Edit form. Coming in T32.</p>
    </div>
  );
}
