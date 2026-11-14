export default function Loading() {
  return (
    <div className="container py-16" role="status" aria-label="Đang tải trang">
      <div className="skeleton h-16! w-1/2 mb-10" />
      <div className="skeleton-grid">
        {[0, 1, 2, 3].map((i) => (
          <div className="skeleton" key={i} />
        ))}
      </div>
      <span className="sr-only">Đang tải trang</span>
    </div>
  );
}
